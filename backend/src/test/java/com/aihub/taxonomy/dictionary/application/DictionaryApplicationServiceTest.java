/*
 * 功能: 字典应用服务单元测试——itemCode 校验、版本自增、冲突与未授权路径覆盖。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.taxonomy.application.AuditPort;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.CreateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryItemView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.UpdateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.domain.DictionaryItem;
import com.aihub.taxonomy.dictionary.domain.DictionaryRepository;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 字典应用服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DictionaryApplicationServiceTest {

    @Mock
    private DictionaryRepository repository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private AuditPort auditPort;

    private DictionaryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DictionaryApplicationService(repository, idGenerator, auditPort,
                Clock.systemUTC());
    }

    @Test
    void validateItemCodePassesForActiveItem() {
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "dict.license_catalog.MIT", 1, TaxonomyStatus.ACTIVE, 1L)));
        service.validateItemCode("license_catalog", "MIT");
    }

    @Test
    void validateItemCodeRejectsDisabledItemForNewReference() {
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "dict.license_catalog.MIT", 1, TaxonomyStatus.DISABLED, 1L)));
        assertThatThrownBy(() -> service.validateItemCode("license_catalog", "MIT"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.DICTIONARY_VALUE_INVALID));
    }

    @Test
    void validateItemCodeRejectsUnknownItem() {
        given(repository.findItem("license_catalog", "NOPE"))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> service.validateItemCode("license_catalog", "NOPE"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void isKnownReturnsTrueForDisabledItem() {
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "dict.license_catalog.MIT", 1, TaxonomyStatus.DISABLED, 1L)));
        assertThat(service.isKnown("license_catalog", "MIT")).isTrue();
    }

    @Test
    void isKnownReturnsFalseForUnknownItem() {
        given(repository.findItem("license_catalog", "NOPE")).willReturn(Optional.empty());
        assertThat(service.isKnown("license_catalog", "NOPE")).isFalse();
    }

    @Test
    void createItemSucceedsAndIncrementsTypeVersion() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItem("license_catalog", "GPL_3_0")).willReturn(Optional.empty());
        given(idGenerator.generate("dct")).willReturn("dct_new");
        DictionaryItemView view = service.createItem("license_catalog",
                new CreateDictionaryItemCommand("GPL_3_0", "dict.license_catalog.GPL_3_0", 5), "admin");
        assertThat(view.itemCode()).isEqualTo("GPL_3_0");
        assertThat(view.version()).isEqualTo(1L);
        verify(repository, times(1)).insertItem(any(DictionaryItem.class));
        verify(repository, times(1)).incrementTypeVersion("license_catalog");
    }

    @Test
    void createItemRejectsInvalidItemCodeFormat() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        assertThatThrownBy(() -> service.createItem("license_catalog",
                new CreateDictionaryItemCommand("bad code!", "k", 0), "admin"))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).insertItem(any());
    }

    @Test
    void createItemConflictsOnDuplicate() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "k", 1, TaxonomyStatus.ACTIVE, 1L)));
        assertThatThrownBy(() -> service.createItem("license_catalog",
                new CreateDictionaryItemCommand("MIT", "k", 0), "admin"))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).errorCode())
                        .isEqualTo(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS));
    }

    @Test
    void updateItemDisablesAndIncrementsVersion() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "k", 1, TaxonomyStatus.ACTIVE, 1L)));
        DictionaryItemView view = service.updateItem("license_catalog", "MIT",
                new UpdateDictionaryItemCommand(null, null, TaxonomyStatus.DISABLED, 1L), "admin");
        assertThat(view.status()).isEqualTo(TaxonomyStatus.DISABLED);
        assertThat(view.version()).isEqualTo(2L);
        verify(repository, times(1)).incrementTypeVersion("license_catalog");
    }

    @Test
    void updateItemRejectsVersionMismatch() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItem("license_catalog", "MIT"))
                .willReturn(Optional.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "k", 1, TaxonomyStatus.ACTIVE, 3L)));
        assertThatThrownBy(() -> service.updateItem("license_catalog", "MIT",
                new UpdateDictionaryItemCommand(null, null, TaxonomyStatus.DISABLED, 1L), "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateItemNotFound() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItem("license_catalog", "NOPE")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateItem("license_catalog", "NOPE",
                new UpdateDictionaryItemCommand(null, null, TaxonomyStatus.DISABLED, 1L), "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listItemsRespectsIncludeDisabled() {
        given(repository.typeExists("license_catalog")).willReturn(true);
        given(repository.findItems(eq("license_catalog"), eq(false)))
                .willReturn(List.of(new DictionaryItem("dct_1", "license_catalog", "MIT",
                        "k", 1, TaxonomyStatus.ACTIVE, 1L)));
        given(repository.findItems(eq("license_catalog"), eq(true)))
                .willReturn(List.of(
                        new DictionaryItem("dct_1", "license_catalog", "MIT",
                                "k", 1, TaxonomyStatus.ACTIVE, 1L),
                        new DictionaryItem("dct_2", "license_catalog", "OLD",
                                "k", 2, TaxonomyStatus.DISABLED, 1L)));
        assertThat(service.listItems("license_catalog", false)).hasSize(1);
        assertThat(service.listItems("license_catalog", true)).hasSize(2);
    }

    @Test
    void listItemsNotFoundForUnknownDict() {
        given(repository.typeExists("nope")).willReturn(false);
        assertThatThrownBy(() -> service.listItems("nope", false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createItemNotFoundForUnknownDict() {
        given(repository.typeExists("nope")).willReturn(false);
        assertThatThrownBy(() -> service.createItem("nope",
                new CreateDictionaryItemCommand("X", "k", 0), "admin"))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).insertItem(any());
        verify(repository, never()).incrementTypeVersion(anyString());
    }
}
