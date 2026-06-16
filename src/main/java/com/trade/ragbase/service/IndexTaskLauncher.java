package com.trade.ragbase.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class IndexTaskLauncher {

    private final IndexService indexService;

    public IndexTaskLauncher(@Lazy IndexService indexService) {
        this.indexService = indexService;
    }

    @Async("indexTaskExecutor")
    public void launchWithText(Long taskId, Long docId, String textContent) {
        indexService.executeWithText(taskId, docId, textContent);
    }
}
