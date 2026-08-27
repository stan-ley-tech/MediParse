package com.mediparse.document;

final class Filenames {

    private Filenames() {
    }

    /** Returns the lowercase extension without the leading dot, or "" if there isn't one. */
    static String extensionWithoutDot(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
