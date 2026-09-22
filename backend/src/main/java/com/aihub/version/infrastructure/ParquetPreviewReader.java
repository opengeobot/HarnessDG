/*
 * 功能: Parquet 预览读取器——有限行/列/字节采样。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parquet 有限预览读取（行/列/字节上限由调用方控制）。
 */
final class ParquetPreviewReader {

    private static final Logger LOG = LoggerFactory.getLogger(ParquetPreviewReader.class);

    private ParquetPreviewReader() {
    }

    static byte[] decodeContent(String content, String encoding) {
        if ("base64".equalsIgnoreCase(encoding)) {
            return Base64.getDecoder().decode(content);
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    static boolean isParquetMagic(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'A' && bytes[2] == 'R' && bytes[3] == '1';
    }

    static List<Map<String, Object>> readRows(byte[] parquetBytes, int maxRows, int maxCols)
            throws IOException {
        if (!isParquetMagic(parquetBytes)) {
            throw new UnsupportedOperationException("PREVIEW_UNSUPPORTED_FORMAT: not a parquet file");
        }
        Path temp = Files.createTempFile("aihub-preview-", ".parquet");
        try {
            Files.write(temp, parquetBytes);
            org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(temp.toUri());
            Configuration conf = new Configuration(false);
            try (ParquetReader<Group> reader = ParquetReader.builder(new GroupReadSupport(), hadoopPath)
                    .withConf(conf)
                    .build()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                Group group;
                while ((group = reader.read()) != null && rows.size() < maxRows) {
                    rows.add(groupToMap(group, maxCols));
                }
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (Exception ex) {
                LOG.debug("parquet row reader failed, falling back to schema metadata", ex);
            }
            return readMetadataOnly(hadoopPath, conf, maxCols);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static List<Map<String, Object>> readMetadataOnly(org.apache.hadoop.fs.Path hadoopPath,
                                                              Configuration conf, int maxCols)
            throws IOException {
        try (ParquetFileReader reader = ParquetFileReader.open(conf, hadoopPath)) {
            var schema = reader.getFooter().getFileMetaData().getSchema();
            List<String> columns = new ArrayList<>();
            schema.getFields().stream().limit(maxCols).forEach(f -> columns.add(f.getName()));
            return List.of(Map.of(
                    "columns", columns,
                    "rowGroups", reader.getFooter().getBlocks().size(),
                    "note", "row data unavailable; schema-only preview"));
        }
    }

    private static Map<String, Object> groupToMap(Group group, int maxCols) {
        Map<String, Object> row = new LinkedHashMap<>();
        int fields = Math.min(group.getType().getFieldCount(), maxCols);
        for (int i = 0; i < fields; i++) {
            String name = group.getType().getFieldName(i);
            int count = group.getFieldRepetitionCount(i);
            if (count == 0) {
                row.put(name, null);
            } else if (count == 1) {
                row.put(name, group.getValueToString(i, 0));
            } else {
                List<String> values = new ArrayList<>();
                for (int j = 0; j < count; j++) {
                    values.add(group.getValueToString(i, j));
                }
                row.put(name, values);
            }
        }
        return row;
    }
}
