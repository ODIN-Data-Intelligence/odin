package com.odin.catalog.ai.tools;

public final class DatasetIdContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private DatasetIdContext() {}

    public static void set(String datasetId) { HOLDER.set(datasetId); }
    public static String get()               { return HOLDER.get(); }
    public static void clear()               { HOLDER.remove(); }
}
