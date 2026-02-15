package com.github.laplusijns;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class InvpoceSessionListener implements HttpSessionListener {

    ImageCache imageCache;
    InvoiceChannels invoiceChannels;
    UserCache userCache;
    
    private static Logger log = LoggerFactory.getLogger(InvpoceSessionListener.class);

    public InvpoceSessionListener(
            final ImageCache imageCache, final InvoiceChannels invoiceChannels, final UserCache userCache) {
        super();
        this.imageCache = imageCache;
        this.invoiceChannels = invoiceChannels;
        this.userCache = userCache;
    }

    @Override
    public void sessionCreated(final HttpSessionEvent se) {
        final String id = se.getSession().getId();
        invoiceChannels.createChannel(se.getSession().getId());
        userCache.put(id, new UserData());
        log.info("session 連接 {}", id);
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent se) {
        final String id = se.getSession().getId();
        final var data = userCache.get(id);
        if (data == null) {
            return;
        }
        final var list = data.getData();
        for (Object object : list) {
            if (object instanceof InvoiceDTO dto) {
                imageCache.delete(dto.imageUrl());
            }
        }
        userCache.delete(id);
        invoiceChannels.cleanUp(se.getSession().getId());
        
        log.info("session 斷開 {}", id);
    }
}
