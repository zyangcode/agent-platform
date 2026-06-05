package com.ls.agent.web.controller;

final class PageRequestNormalizer {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private PageRequestNormalizer() {
    }

    static int pageNo(int pageNo) {
        return pageNo <= 0 ? DEFAULT_PAGE_NO : pageNo;
    }

    static int pageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
