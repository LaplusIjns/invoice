package com.github.laplusijns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserData {

    List<InvoiceDTO> data = Collections.synchronizedList(new ArrayList<>(64));

    public List<InvoiceDTO> getData() {
        return data;
    }

    public void setData(final List<InvoiceDTO> data) {
        this.data = data;
    }
}
