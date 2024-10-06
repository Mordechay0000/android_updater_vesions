package com.mordechay.androidCustomUpdater;

import java.util.ArrayList;
import java.util.Arrays;

public class Structure {
    private String version_information;
    private String[] button_texts;
    private String[] url_download;

    public String getVersion_information() {
        return version_information;
    }

    public void setVersion_information(String version_information) {
        this.version_information = version_information;
    }

    public String[] getButton_texts() {
        return button_texts;
    }

    public void setButton_texts(String[] button_texts) {
        this.button_texts = button_texts;
    }

    public String[] getUrl_download() {
        return url_download;
    }

    public void setUrl_download(String[] url_download) {
        this.url_download = url_download;
    }
}
