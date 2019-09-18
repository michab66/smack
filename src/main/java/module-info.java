/**
 * $Id$
 */
module framework.smack {
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires javafx.base;
    requires javafx.controls;

    uses org.jdesktop.util.ResourceConverter;
    uses org.jdesktop.util.ResourceConverterExtension;

    // All converters have to be registered here.  The ServiceLoader
    // uses this list. The ServiceLoader configuration file is not
    // needed with the module system.
    provides org.jdesktop.util.ResourceConverterExtension with
        org.jdesktop.util.converters.PrimitivesBundle;
    provides org.jdesktop.util.ResourceConverter with
        org.jdesktop.util.converters.StringStringConverter,
        org.jdesktop.util.converters.StringStringBuilderConverter,
        org.jdesktop.util.converters.StringArrayRc,
        org.jdesktop.util.converters.UrlStringConverter,
        org.jdesktop.util.converters.UriStringConverter,
        org.jdesktop.util.converters.ColorStringConverter,
        org.jdesktop.util.converters.DimensionStringConverter,
        org.jdesktop.util.converters.EmptyBorderStringConverter,
        org.jdesktop.util.converters.FontStringConverter,
        org.jdesktop.util.converters.FxImageConverter,
        org.jdesktop.util.converters.IconStringConverter,
        org.jdesktop.util.converters.ImageStringConverter,
        org.jdesktop.util.converters.InsetsStringConverter,
        org.jdesktop.util.converters.KeyStrokeStringConverter,
        org.jdesktop.util.converters.Point2dStringConverter,
        org.jdesktop.util.converters.PointStringConverter,
        org.jdesktop.util.converters.RectangleStringConverter;

    exports org.jdesktop.application;
    exports org.jdesktop.beans;
    exports org.jdesktop.smack.util;
    exports org.jdesktop.util;
    exports org.jdesktop.util.converters;
    exports org.smack.fx;
    exports org.smack.util;
}
