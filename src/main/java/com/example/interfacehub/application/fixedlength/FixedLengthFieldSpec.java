package com.example.interfacehub.application.fixedlength;

public record FixedLengthFieldSpec(
    String name,
    int length,
    String padChar,
    String align,
    boolean required,
    boolean trim,
    String defaultValue
) {
    public String resolvedPadChar() {
        if (padChar == null || padChar.isBlank()) {
            return " ";
        }
        return padChar;
    }

    public boolean rightAlign() {
        return align != null && align.equalsIgnoreCase("RIGHT");
    }
}

