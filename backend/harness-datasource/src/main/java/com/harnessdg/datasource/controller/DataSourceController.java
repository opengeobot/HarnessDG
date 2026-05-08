/**
 * 功能：数据源 Controller
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.datasource.controller;

import com.harnessdg.common.response.R;
import com.harnessdg.datasource.service.DataSourceService;
import com.harnessdg.model.datasource.dto.DataSourceCreateRequest;
import com.harnessdg.model.datasource.dto.DataSourceDTO;
import com.harnessdg.model.datasource.dto.IngestionTaskCreateRequest;
import com.harnessdg.model.datasource.dto.IngestionTaskDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    // === Data Source APIs ===

    @GetMapping
    public R<List<DataSourceDTO>> listDataSources(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status) {
        return R.ok(dataSourceService.listDataSources(sourceType, status));
    }

    @GetMapping("/{id}")
    public R<DataSourceDTO> getDataSource(@PathVariable Long id) {
        return R.ok(dataSourceService.getDataSource(id));
    }

    @PostMapping
    public R<DataSourceDTO> createDataSource(@Valid @RequestBody DataSourceCreateRequest request) {
        return R.ok(dataSourceService.createDataSource(request));
    }

    @PutMapping("/{id}")
    public R<DataSourceDTO> updateDataSource(
            @PathVariable Long id,
            @Valid @RequestBody DataSourceCreateRequest request) {
        return R.ok(dataSourceService.updateDataSource(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteDataSource(@PathVariable Long id) {
        dataSourceService.deleteDataSource(id);
        return R.ok(null);
    }

    // === Ingestion Task APIs ===

    @GetMapping("/tasks")
    public R<List<IngestionTaskDTO>> listAllTasks(
            @RequestParam(required = false) String status) {
        return R.ok(dataSourceService.listAllTasks(status));
    }

    @GetMapping("/{sourceId}/tasks")
    public R<List<IngestionTaskDTO>> listTasks(
            @PathVariable Long sourceId,
            @RequestParam(required = false) String status) {
        return R.ok(dataSourceService.listTasks(sourceId, status));
    }

    @GetMapping("/tasks/{id}")
    public R<IngestionTaskDTO> getTask(@PathVariable Long id) {
        return R.ok(dataSourceService.getTask(id));
    }

    @PostMapping("/tasks")
    public R<IngestionTaskDTO> createTask(@Valid @RequestBody IngestionTaskCreateRequest request) {
        return R.ok(dataSourceService.createTask(request));
    }

    @PutMapping("/tasks/{id}")
    public R<IngestionTaskDTO> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody IngestionTaskCreateRequest request) {
        return R.ok(dataSourceService.updateTask(id, request));
    }

    @DeleteMapping("/tasks/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        dataSourceService.deleteTask(id);
        return R.ok(null);
    }
}
