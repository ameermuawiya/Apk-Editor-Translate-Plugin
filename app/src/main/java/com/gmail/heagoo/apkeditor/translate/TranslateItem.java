package com.gmail.heagoo.apkeditor.translate;

import java.io.Serializable;

/*
 * Represents a single translation string item.
 * Essential for compatibility with the main APK Editor app.
 */
public class TranslateItem implements Serializable {
    private static final long serialVersionUID = -3101805950698159689L;
    public String name;
    public String originValue;
    public String translatedValue;

    public TranslateItem(String name, String originValue) {
        this.name = name;
        this.originValue = originValue;
    }

    public TranslateItem(String name, String originValue, String translatedValue) {
        this.name = name;
        this.originValue = originValue;
        this.translatedValue = translatedValue;
    }
}
