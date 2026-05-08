/**
 * 功能：数据源 Service 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.datasource.service;

import com.harnessdg.model.datasource.dto.DataSourceCreateRequest;
import com.harnessdg.model.datasource.dto.DataSourceDTO;
import com.harnessdg.model.datasource.dto.IngestionTaskCreateRequest;
import com.harnessdg.model.datasource.dto.IngestionTaskDTO;

import java.util.List;

public interface DataSourceService {

    List<DataSourceDTO> listDataSources(String sourceType, String status);

    DataSourceDTO getDataSource(Long id);

    DataSourceDTO createDataSource(DataSourceCreateRequest request);

    DataSourceDTO updateDataSource(Long id, DataSourceCreateRequest request);

    void deleteDataSource(Long id);

    List<IngestionTaskDTO> listAllTasks(String status);

    List<IngestionTaskDTO> listTasks(Long sourceId, String status);

    IngestionTaskDTO getTask(Long id);

    IngestionTaskDTO createTask(IngestionTaskCreateRequest request);

    IngestionTaskDTO updateTask(Long id, IngestionTaskCreateRequest request);

    void deleteTask(Long id);
}
