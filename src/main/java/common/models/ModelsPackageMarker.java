package common.models;

/**
 * Marker type to keep the root package non-empty for JPMS exports/opens.
 */
public final class ModelsPackageMarker {
    private ModelsPackageMarker() {
        // Utility marker class; not meant to be instantiated.
    }
}
