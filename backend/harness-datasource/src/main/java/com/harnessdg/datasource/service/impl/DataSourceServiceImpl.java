/**
 * 功能：数据源 Service 实现
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.datasource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.datasource.mapper.DataSourceMapper;
import com.harnessdg.datasource.mapper.IngestionTaskMapper;
import com.harnessdg.datasource.service.DataSourceService;
import com.harnessdg.model.datasource.dto.DataSourceCreateRequest;
import com.harnessdg.model.datasource.dto.DataSourceDTO;
import com.harnessdg.model.datasource.dto.IngestionTaskCreateRequest;
import com.harnessdg.model.datasource.dto.IngestionTaskDTO;
import com.harnessdg.model.datasource.entity.DataSource;
import com.harnessdg.model.datasource.entity.IngestionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final IngestionTaskMapper ingestionTaskMapper;

    @Override
    public List<DataSourceDTO> listDataSources(String sourceType, String status) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        if (sourceType != null && !sourceType.isBlank()) {
            wrapper.eq(DataSource::getSourceType, sourceType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(DataSource::getStatus, status);
        }
        wrapper.orderByDesc(DataSource::getCreatedAt);
        return dataSourceMapper.selectList(wrapper).stream()
                .map(this::toDataSourceDTO)
                .collect(Collectors.toList());
    }

    @Override
    public DataSourceDTO getDataSource(Long id) {
        DataSource entity = dataSourceMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        return toDataSourceDTO(entity);
    }

    @Override
    @Transactional
    public DataSourceDTO createDataSource(DataSourceCreateRequest request) {
        // 检查 code 唯一性
        LambdaQueryWrapper<DataSource> check = new LambdaQueryWrapper<>();
        check.eq(DataSource::getCode, request.getCode());
        if (dataSourceMapper.selectCount(check) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据源 code 已存在");
        }

        DataSource entity = new DataSource();
        entity.setName(request.getName());
        entity.setCode(request.getCode());
        entity.setSourceType(request.getSourceType());
        entity.setConnectionConfig(request.getConnectionConfig());
        entity.setDescription(request.getDescription());
        entity.setOwner(request.getOwner());
        entity.setTags(request.getTags());
        entity.setStatus("active");
        dataSourceMapper.insert(entity);
        return toDataSourceDTO(entity);
    }

    @Override
    @Transactional
    public DataSourceDTO updateDataSource(Long id, DataSourceCreateRequest request) {
        DataSource entity = dataSourceMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getSourceType() != null) entity.setSourceType(request.getSourceType());
        if (request.getConnectionConfig() != null) entity.setConnectionConfig(request.getConnectionConfig());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getOwner() != null) entity.setOwner(request.getOwner());
        if (request.getTags() != null) entity.setTags(request.getTags());

        dataSourceMapper.updateById(entity);
        return toDataSourceDTO(entity);
    }

    @Override
    @Transactional
    public void deleteDataSource(Long id) {
        DataSource entity = dataSourceMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        dataSourceMapper.deleteById(id);
    }

    @Override
    public List<IngestionTaskDTO> listAllTasks(String status) {
        LambdaQueryWrapper<IngestionTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(IngestionTask::getStatus, status);
        }
        wrapper.orderByDesc(IngestionTask::getCreatedAt);
        return ingestionTaskMapper.selectList(wrapper).stream()
                .map(this::toTaskDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<IngestionTaskDTO> listTasks(Long sourceId, String status) {
        LambdaQueryWrapper<IngestionTask> wrapper = new LambdaQueryWrapper<>();
        if (sourceId != null) {
            wrapper.eq(IngestionTask::getSourceId, sourceId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(IngestionTask::getStatus, status);
        }
        wrapper.orderByDesc(IngestionTask::getCreatedAt);
        return ingestionTaskMapper.selectList(wrapper).stream()
                .map(this::toTaskDTO)
                .collect(Collectors.toList());
    }

    @Override
    public IngestionTaskDTO getTask(Long id) {
        IngestionTask entity = ingestionTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        return toTaskDTO(entity);
    }

    @Override
    @Transactional
    public IngestionTaskDTO createTask(IngestionTaskCreateRequest request) {
        // 检查 taskCode 唯一性
        LambdaQueryWrapper<IngestionTask> check = new LambdaQueryWrapper<>();
        check.eq(IngestionTask::getTaskCode, request.getTaskCode());
        if (ingestionTaskMapper.selectCount(check) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任务 code 已存在");
        }

        IngestionTask entity = new IngestionTask();
        entity.setSourceId(request.getSourceId());
        entity.setTargetEntityId(request.getTargetEntityId());
        entity.setTaskName(request.getTaskName());
        entity.setTaskCode(request.getTaskCode());
        entity.setSyncMode(request.getSyncMode());
        entity.setSourceTable(request.getSourceTable());
        entity.setFieldMapping(request.getFieldMapping());
        entity.setScheduleCron(request.getScheduleCron());
        entity.setStatus("pending");
        ingestionTaskMapper.insert(entity);
        return toTaskDTO(entity);
    }

    @Override
    @Transactional
    public IngestionTaskDTO updateTask(Long id, IngestionTaskCreateRequest request) {
        IngestionTask entity = ingestionTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }

        if (request.getSourceId() != null) entity.setSourceId(request.getSourceId());
        if (request.getTargetEntityId() != null) entity.setTargetEntityId(request.getTargetEntityId());
        if (request.getTaskName() != null) entity.setTaskName(request.getTaskName());
        if (request.getSyncMode() != null) entity.setSyncMode(request.getSyncMode());
        if (request.getSourceTable() != null) entity.setSourceTable(request.getSourceTable());
        if (request.getFieldMapping() != null) entity.setFieldMapping(request.getFieldMapping());
        if (request.getScheduleCron() != null) entity.setScheduleCron(request.getScheduleCron());

        ingestionTaskMapper.updateById(entity);
        return toTaskDTO(entity);
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        IngestionTask entity = ingestionTaskMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        ingestionTaskMapper.deleteById(id);
    }

    private DataSourceDTO toDataSourceDTO(DataSource entity) {
        DataSourceDTO dto = new DataSourceDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setSourceType(entity.getSourceType());
        dto.setConnectionConfig(entity.getConnectionConfig());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        dto.setOwner(entity.getOwner());
        dto.setTags(entity.getTags());
        return dto;
    }

    private IngestionTaskDTO toTaskDTO(IngestionTask entity) {
        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setId(entity.getId());
        dto.setSourceId(entity.getSourceId());
        dto.setTargetEntityId(entity.getTargetEntityId());
        dto.setTaskName(entity.getTaskName());
        dto.setTaskCode(entity.getTaskCode());
        dto.setSyncMode(entity.getSyncMode());
        dto.setSourceTable(entity.getSourceTable());
        dto.setFieldMapping(entity.getFieldMapping());
        dto.setScheduleCron(entity.getScheduleCron());
        dto.setSeatunnelJobId(entity.getSeatunnelJobId());
        dto.setDagsterRunId(entity.getDagsterRunId());
        dto.setStatus(entity.getStatus());
        dto.setLastSyncAt(entity.getLastSyncAt());
        dto.setLastSyncRows(entity.getLastSyncRows());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setApprovalInstanceId(entity.getApprovalInstanceId());
        return dto;
    }
}
