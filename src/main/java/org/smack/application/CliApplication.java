/* $Id$
 *
 * Released under Gnu Public License
 * Copyright © 2013-2019 Michael G. Binz
 */
package org.smack.application;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smack.util.JavaUtil;
import org.smack.util.StringUtil;
import org.smack.util.collections.MultiMap;

/**
 * A base class for console applications.
 *
 * @author MICKARG
 * @author MICBINZ
 */
abstract public class CliApplication
{
    private static final Logger LOG =
            Logger.getLogger( CliApplication.class.getName() );

    /**
     * Used to mark cli command operations.
     */
    @Retention( RetentionPolicy.RUNTIME )
    @Target( ElementType.METHOD )
    protected @interface Command {
        String name() default StringUtil.EMPTY_STRING;
        String[] argumentNames() default {};
        String shortDescription() default StringUtil.EMPTY_STRING;
    }

    /**
     * Used to add information on the implementation class.
     */
    @Retention( RetentionPolicy.RUNTIME )
    @Target( {ElementType.TYPE, ElementType.PARAMETER} )
    public @interface Named {
        String value()
            default StringUtil.EMPTY_STRING;
        String description()
            default StringUtil.EMPTY_STRING;
    }

    private static class CaseIndependent
    {
        private final String _name;

        CaseIndependent( String name )
        {
            _name =
                    Objects.requireNonNull(  name );
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( null == obj )
                return false;
            if ( obj == this )
                return true;

            return _name.equalsIgnoreCase( obj.toString() );
        }

        @Override
        public int hashCode()
        {
            return _name.toLowerCase().hashCode();
        }

        @Override
        public String toString()
        {
            return _name;
        }
    }

    /**
     * A name to be used if the command should be callable without
     * a dedicated name.
     */
    public static final CaseIndependent UNNAMED =
            new CaseIndependent( "*" );

    /**
     * A map of all commands implemented by this cli. Keys are
     * command name and number of arguments, the value represents
     * the respective method.
     */
    private final MultiMap<CaseIndependent, Integer, CommandHolder> _commandMap =
            getCommandMap( getClass() );

    public interface StringConverter<T>
    {
        T convert( String s ) throws Exception;
    }

    private static final HashMap<Class<?>,StringConverter<?>> _converters =
            new HashMap<>();

    protected final static void addConverter( Class<?> cl, StringConverter<?> c )
    {
        _converters.put( cl, c );
    }

    static
    {
        addConverter(
                String.class,
                (s) -> s );
        addConverter(
                Byte.TYPE,
                CliApplication::stringToByte );
        addConverter(
                Short.TYPE,
                CliApplication::stringToShort );
        addConverter(
                Integer.TYPE,
                CliApplication::stringToInt );
        addConverter(
                Long.TYPE,
                CliApplication::stringToLong );
        addConverter(
                File.class,
                CliApplication::stringToFile );
        addConverter(
                Boolean.TYPE,
                CliApplication::stringToBoolean );
        addConverter(
                Float.TYPE,
                CliApplication::stringToFloat );
        addConverter(
                Double.TYPE,
                CliApplication::stringToDouble );
    }

    private String _currentCommand =
            StringUtil.EMPTY_STRING;

    protected final String currentCommand()
    {
        return _currentCommand;
    }

    /**
     * A fallback called if no command was passed or the passed command was
     * unknown.
     *
     * @param argv
     *            The received command line.
     * @throws Exception In case of errors.
     */
    protected void defaultCmd( String[] argv )
            throws Exception
    {
        err( usage() );
    }

    /**
     * Perform the launch of the cli instance.
     */
    private void launchInstance( String[] argv )
            throws Exception
    {
        if ( argv.length == 0 ) {
            defaultCmd(argv);
            return;
        }

        if ( argv.length == 1 && argv[0].equals("?") ) {
            err(usage());
            return;
        }

        var ciName =
                new CaseIndependent( argv[0] );

        CommandHolder selectedCommand = _commandMap.get(
            ciName,
            Integer.valueOf(argv.length - 1) );

        if ( selectedCommand != null )
        {
            // We found a matching command.
            _currentCommand =
                    selectedCommand.getName() ;
            selectedCommand.execute(
                    Arrays.copyOfRange( argv, 1, argv.length ) );
            return;
        }

        // No command matched, so we check if there are commands
        // where at least the command name matches.
        Map<Integer, CommandHolder> possibleCommands =
                _commandMap.getAll( ciName );
        if ( possibleCommands.size() > 0 )
        {
            System.err.println(
                    "Parameter count does not match. Available alternatives:" );
            System.err.println(
                    getCommandsUsage(possibleCommands, argv));
            return;
        }

        // Check if we got an unnamed command.
        selectedCommand = _commandMap.get(
                UNNAMED,
                argv.length );
        if ( selectedCommand != null )
        {
            _currentCommand =
                    selectedCommand.getName() ;
            selectedCommand.execute(
                    argv );
            return;
        }

        // Nothing matched, we forward this to default handling.
        defaultCmd( argv );
    }

    /**
     * Start execution of the console command. This implicitly parses
     * the parameters and dispatches the call to the matching operation.
     * <p>
     * The main operation of an application using {@link #CliApplication()}
     * usually looks like:
     * </p>
     *
     * <pre>
     * <code>
     * public class Foo extends CliApplication
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Foo.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl The implementation class of the console command.
     * @param argv The unmodified parameter array.
     */
    static public void launch( Class<? extends CliApplication> cl, String[] argv )
    {
            launch(
                    new DefaultCtorReflection<>( cl ),
                    argv );
    }

    /**
     * Start execution of the console command. This implicitly parses
     * the parameters and dispatches the call to the matching operation.
     * <p>
     * The main operation of an application using {@link #CliApplication()} usually looks like:
     * </p>
     *
     * <pre>
     * <code>
     * public class Duck extends CliApplication
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Duck.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl The implementation class of the console command.
     * @param argv The unmodified parameter array.
     */
    static public void launch( Supplier<CliApplication> cl, String[] argv )
    {
        try
        {
            cl.get().launchInstance( argv );
        }
        catch (RuntimeException e)
        {
            String msg = e.getMessage();
            if (msg == null)
                msg = e.getClass().getName();

            LOG.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        }
        catch (Exception e)
        {
            String msg = e.getMessage();
            if (msg == null)
                msg = e.getClass().getName();

            LOG.log(Level.FINE, msg, e);
            System.err.println("Failed: " + msg);
        }
    }

    /**
     * Get String for error handling with correct function calls.
     *
     * @param commands
     *            Map with all possible commands.
     * @param argv
     *            Argument list as String.
     * @return Usage message for error handling.
     */
    private String getCommandsUsage(Map<Integer, CommandHolder> commands, String[] argv)
    {
        StringBuilder result = new StringBuilder();

        commands.values().forEach(
                c -> result.append( c.usage() ));

        return result.toString();
    }

    /**
     * Usage function to get a dynamic help text with all available commands.
     *
     * @return Usage text.
     */
    protected String usage()
    {
        StringBuilder result =
                new StringBuilder( getApplicationName() );
        {
            String desc =
                    getApplicationDescription();
            if ( StringUtil.hasContent( desc ) )
            {
                result.append( " -- " );
                result.append(desc);
            }
        }
        result.append( "\n\nThe following commands are supported:\n\n" );

        for ( CommandHolder command : sort( _commandMap.getValues() ) )
            result.append( command.usage() );

        return result.toString();
    }

    /**
     * Helper operation to sort a collection of methods.
     *
     * @return A newly allocated list.
     */
    private List<CommandHolder> sort( Collection<CommandHolder> methods )
    {
        var result = new ArrayList<>( methods );

        Collections.sort( result, null );

        return result;
    }

    /**
     * Get a map of all commands that allows to access a single command based on
     * its name and argument list.
     */
    private MultiMap<CaseIndependent, Integer, CommandHolder> getCommandMap(
            Class<?> targetClass )
    {
        MultiMap<CaseIndependent,Integer,CommandHolder> result =
                new MultiMap<>();

        for ( Method c : targetClass.getDeclaredMethods() )
        {
            Command commandAnnotation =
                c.getAnnotation( Command.class );
            if ( commandAnnotation == null )
                continue;

            String name = commandAnnotation.name();
            if ( StringUtil.isEmpty( name ) )
                name = c.getName();

            for ( Class<?> current : c.getParameterTypes() )
            {
                if ( current.isEnum() )
                    continue;

                Objects.requireNonNull(
                        _converters.get( current ),
                        "No mapper for " + current );
            }

            Integer numberOfArgs =
                    Integer.valueOf( c.getParameterTypes().length );

            var currentName =
                    new CaseIndependent( name );
            // Check if we already have this command with the same parameter
            // list length. This is an implementation error.
            if (result.get(currentName, numberOfArgs) != null) {
                throw new InternalError(
                        "Implementation error. Operation " +
                        name +
                        " with " +
                        numberOfArgs +
                        " parameters is not unique.");
            }

            result.put(
                    currentName,
                    numberOfArgs,
                    new CommandHolder( c ) );
        }

        return result;
    }

    /**
     * Handle an exception thrown from a command. This default implementation
     * prints the exception message or, if this is empty, the exception name.
     * <p>In addition it tries to differentiate between implementation errors
     * and logical errors. RuntimeExceptions and Errors are handled as
     * implementation errors and printed including their stack trace.</p>
     *
     * @param e The exception to handle.
     * @param commandName The name of the failing command.
     */
    protected void processCommandException( String commandName, Throwable e )
    {
        String msg = e.getMessage();

        if ( StringUtil.isEmpty( msg ) )
            msg = e.getClass().getName();

        if ( e instanceof RuntimeException || e instanceof Error )
        {
            // Special handling of implementation or VM errors.
            err( "%s failed.\n",
                    commandName );
            e.printStackTrace();
        }
        else
        {
            err( "%s failed: %s\n",
                    commandName,
                    msg );
        }
    }

    /**
     * Transform function for a primitive byte.
     */
    private static byte stringToByte(String arg) throws Exception {
        try {
            return Byte.decode(arg).byteValue();
        }
        catch (NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive short.
     */
    private static short stringToShort(String arg) throws Exception {
        try {
            return Short.decode(arg).shortValue();
        }
        catch (NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive integer.
     */
    private static int stringToInt(String arg) throws Exception {
        try {
            // The long conversion is deliberately used
            // to be able to convert 32bit unsigned integers
            // like 0xffffffe8.
            return Long.decode(arg).intValue();
        }
        catch (NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive long.
     */
    private static long stringToLong(String arg) throws Exception {
        try {
            return Long.decode(arg).longValue();
        }
        catch (NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive float.
     */
    private static float stringToFloat(String arg) throws Exception {
        try {
            return Float.parseFloat( arg );
        }
        catch (NumberFormatException e) {
            throw new Exception( "Not a float: " + arg );
        }
    }

    /**
     * Transform function for a primitive double.
     */
    private static double stringToDouble(String arg) throws Exception {
        try {
            return Double.parseDouble( arg );
        }
        catch (NumberFormatException e) {
            throw new Exception("Not a double: " + arg);
        }
    }

    /**
     * Transform function for File. This ensures that the file exists.
     *
     * @param fileName The name of the file.
     * @throws Exception If the file does not exist.
     * @return A reference to a file instance if one exists.
     */
    private static File stringToFile(String fileName) throws Exception {

        File file = new File(fileName);

        if (!file.exists())
            throw new Exception("File not found: " + file);

        return file;
    }

    /**
     * Transform a string to a boolean.
     *
     * @param arg
     *            Accepts case independent 'TRUE' and 'FALSE' as valid strings.
     * @return The corresponding boolean.
     * @throws Exception In case of a conversion error.
     */
    private static boolean stringToBoolean(String arg) throws Exception {
        if (Boolean.TRUE.toString().equalsIgnoreCase(arg))
            return true;
        else if (Boolean.FALSE.toString().equalsIgnoreCase(arg))
            return false;

        throw new Exception(
            "Expected boolean: true or false. Received '" + arg + "'.");
    }

    private String getEnumDocumentation( Class<?> c )
    {
        List<String> enumNames = new ArrayList<>();

        for ( Object o : c.getEnumConstants() )
            enumNames.add( o.toString() );

        if ( enumNames.isEmpty() )
            return StringUtil.EMPTY_STRING;

        Collections.sort( enumNames );

        return
                "[" +
                StringUtil.concatenate( ", ", enumNames ) +
                "]";
    }

    /**
     * Get the application name. That is the name printed in the headline
     * of generated documentation. If the application is running from
     * CliConsole this is the name that has to specified on the command
     * line.  If the application is run via java -jar application.jar
     * then the returned value has only impact on the generated docs.
     * Add this information by applying the Named annotation on your
     * implementation class.
     *
     * @return The application name.
     */
    public String getApplicationName()
    {
        Named annotation =
                getClass().getAnnotation( Named.class );

        if ( annotation != null && StringUtil.hasContent( annotation.value() ) )
            return annotation.value();

        return getClass().getSimpleName();
    }

    /**
     * Get textual information on the overall console application.
     * Add this information by applying the Named annotation on your
     * implementation class.
     *
     * @return Overall application documentation.
     * @see #getApplicationName()
     */
    protected String getApplicationDescription()
    {
        Named annotation =
                getClass().getAnnotation( Named.class );

        if ( annotation != null && StringUtil.hasContent( annotation.description() ) )
            return annotation.description();

        return StringUtil.EMPTY_STRING;
    }

    /**
     * Convert an argument string to a typed object. Uses
     * a special mapping for enums and the type map
     * for all other types.
     */
    private final Object transformArgument(
            Class<?> targetType,
            String argument )
        throws Exception
    {
        targetType =
                Objects.requireNonNull( targetType );
        argument =
                Objects.requireNonNull( argument );

        // Special handling for enums.
        if ( targetType.isEnum() )
            return transformEnum( targetType, argument );

        StringConverter<?> transformer =
                Objects.requireNonNull(
                        _converters.get( targetType ),
                        "No mapper for " + targetType.getSimpleName() );

        return transformer.convert( argument );
    }

    /**
     * Convert an argument to an enum instance.
     */
    private final Object transformEnum(
            Class<?> targetEnum,
            String argument )
        throws IllegalArgumentException
    {
        if ( targetEnum == null )
            throw new NullPointerException();
        if ( argument == null )
            throw new NullPointerException();
        if ( ! targetEnum.isEnum() )
            throw new AssertionError();

        // Handle enums.
        for ( Object c : targetEnum.getEnumConstants() )
        {
            if ( c.toString().equalsIgnoreCase( argument ) )
                return c;
        }

        // Above went wrong, generate a good message.
        List<String> allowed = new ArrayList<>();
        for ( Object c : targetEnum.getEnumConstants() )
            allowed.add( c.toString() );

        String message = String.format(
                "Unknown enum value: '%s'.  Allowed values are %s.",
                argument,
                StringUtil.concatenate( ", ", allowed ) );

        throw new IllegalArgumentException( message );
    }

    /**
     * Format the parameters to the standard error stream.
     *
     * @param fmt The format string.
     * @param argv Format parameters.
     */
    protected final void err( String fmt, Object ... argv )
    {
        System.err.printf( fmt, argv );
    }

    /**
     * Print to the standard error stream. Note that no
     * line feed is added.
     *
     * @param msg The message to print.
     */
    protected final void err( String msg )
    {
        System.err.print( msg );
    }

    /**
     * Format the parameters to the standard output stream.
     *
     * @param fmt The format string.
     * @param argv Format parameters.
     */
    protected final void out( String fmt, Object ... argv )
    {
        System.out.printf( fmt, argv );
    }

    /**
     * Print to the standard output stream. Note that no
     * line feed is added.
     *
     * @param msg The message to print.
     */
    protected final void out( String msg )
    {
        System.out.print( msg );
    }

    /**
     * Shorthand for System.in.
     *
     * @return The standard input stream.
     */
    protected final InputStream in()
    {
        return System.in;
    }

    private static class DefaultCtorReflection<T extends CliApplication>
        implements Supplier<CliApplication>
    {
        private final Class<T> _class;

        public DefaultCtorReflection( Class<T> claß )
        {
            _class = claß;
        }

        @Override
        public CliApplication get()
        {
            try {
                Constructor<T> c = _class.getDeclaredConstructor();
                return c.newInstance();
            }
            catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Encapsulates a command.
     */
    private class CommandHolder implements Comparable<CommandHolder>
    {
        private final Method _op;
        private final Command _commandAnnotation;

        CommandHolder( Method operation )
        {
            _op =
                    operation;
            _commandAnnotation =
                    Objects.requireNonNull(
                            _op.getAnnotation( Command.class ),
                            "@Command missing." );
        }

        String getName()
        {
            var result = _commandAnnotation.name();

            if ( StringUtil.hasContent( result ))
                return result;

            return _op.getName();
        }

        int getParameterCount()
        {
            return _op.getParameterCount();
        }

        private String getDescription()
        {
            return _commandAnnotation.shortDescription();
        }

        /**
         * Execute the passed command with the given passed arguments. Each parameter
         * is transformed to the expected type.
         *
         * @param command
         *            Command to execute.
         * @param argv
         *            List of arguments.
         */
        private void execute( String ... argv )
        {
            Object[] arguments =
                    new Object[argv.length];
            Class<?>[] params =
                    _op.getParameterTypes();

            if ( argv.length != params.length )
                throw new AssertionError();

            for (int j = 0; j < params.length; j++) try {
                arguments[j] = transformArgument(
                        params[j],
                        argv[j] );
            } catch ( Exception e ) {
                err("Parameter %s : ", argv[j]);

                String msg = e.getMessage();

                if ( StringUtil.isEmpty( msg ) )
                    err( e.getClass().getSimpleName() );
                else
                    err( msg );

                return;
            }

            try {
                final var self = CliApplication.this;

                if ( ! _op.canAccess( self ) )
                    _op.setAccessible( true );

                _op.invoke(
                        self,
                        arguments);
            }
            catch ( InvocationTargetException e )
            {
                processCommandException( _op.getName(), e.getCause() );
            }
            catch ( Exception e )
            {
                // A raw exception must come from our implementation,
                // so we present a less user friendly stacktrace.
                e.printStackTrace();
            }
            finally
            {
                // In case a parameter conversion operation created
                // 'closeable' objects, ensure that these get freed.
                for ( Object c : arguments )
                {
                    if ( c instanceof AutoCloseable )
                        JavaUtil.force( ((AutoCloseable)c)::close );
                }
            }
        }

        private String getParameterList()
        {
            String[] list = getCommandParameterListExt();

            if ( list.length == 0 )
                return StringUtil.EMPTY_STRING;

            return StringUtil.concatenate( ", ", list );
        }

        private String[] getCommandParameterListExt()
        {
            Class<?>[] parameterTypes =
                    _op.getParameterTypes();

            // The old-style command parameter documentation has priority.
            if ( _commandAnnotation.argumentNames().length > 0 )
            {
                if ( _commandAnnotation.argumentNames().length != parameterTypes.length )
                    LOG.warning( "Command.argumentNames in consistent with " + _op );

                return _commandAnnotation.argumentNames();
            }

            // The strategic way of defining parameter documentation.
            String[] result = new String[ getParameterCount() ];
            int idx = 0;
            for ( Parameter c : _op.getParameters() )
            {
                Named named = c.getDeclaredAnnotation( Named.class );

                if ( named != null && StringUtil.hasContent( named.value() ) )
                    result[idx] = named.value();
                else if ( c.getType().isEnum() )
                    result[idx] = getEnumDocumentation( c.getType() );
                else
                    result[idx] = c.getType().getSimpleName();

                idx++;
            }

            return result;
        }

        /**
         * Generate help text for a method.
         */
        private String usage()
        {
            StringBuilder info = new StringBuilder();

            info.append( getName() );

            String optional =
                    getParameterList();
            if ( StringUtil.hasContent( optional ) )
            {
                info.append( ": " );
                info.append( optional );
            }
            info.append( "\n" );

            optional =
                    getDescription();
            if ( StringUtil.hasContent( optional ) )
            {
                info.append( "    " );
                info.append( optional );
                info.append( "\n" );
            }

            return info.toString();
        }

        @Override
        public int compareTo( CommandHolder o )
        {
            int result =
                    getName().compareTo( o.getName() );

            if ( result != 0 )
                return result;

            return
                    getParameterCount() -
                    o.getParameterCount();
        }
    }
}