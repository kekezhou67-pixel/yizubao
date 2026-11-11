package com.rental.platform.service;

import java.util.Map;

public interface ContractReviewService {

    /**
     * 审查合同
     * @param contentText 合同文本内容
     * @param userId 用户ID
     * @return 审查结果（含AI分析和风险点）
     */
    Map<String, Object> reviewContract(String contentText, Long userId);
}

