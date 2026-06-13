package com.company.generator.manager.service.impl;

import com.company.generator.manager.entity.GenerationLog;
import com.company.generator.manager.mapper.GenerationLogMapper;
import com.company.generator.manager.service.IGenerationLogService;
import com.company.manerger.sys.common.mybatis.service.impl.CommonServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service("generationLogService")
public class GenerationLogServiceImpl extends CommonServiceImpl<GenerationLogMapper, GenerationLog> implements IGenerationLogService {
}
