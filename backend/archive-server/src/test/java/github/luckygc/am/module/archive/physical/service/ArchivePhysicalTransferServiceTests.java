package github.luckygc.am.module.archive.physical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveVolumeService;
import github.luckygc.am.module.archive.physical.ArchivePhysicalCustodyStatus;
import github.luckygc.am.module.archive.physical.ArchivePhysicalObject;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransfer;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransferItem;
import github.luckygc.am.module.archive.physical.ArchivePhysicalTransferStatus;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalObjectDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalTransferDataRepository;
import github.luckygc.am.module.archive.physical.repository.ArchivePhysicalTransferItemDataRepository;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.AcceptArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.CreateArchivePhysicalTransferRequest;
import github.luckygc.am.module.archive.physical.service.ArchivePhysicalTransferService.RejectArchivePhysicalTransferRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;
import github.luckygc.am.module.organization.service.OrganizationDepartmentService;

@DisplayName("业务部门实物移交接收")
class ArchivePhysicalTransferServiceTests {

    private ArchivePhysicalTransferDataRepository transferRepository;
    private ArchivePhysicalTransferItemDataRepository itemRepository;
    private ArchivePhysicalObjectDataRepository physicalObjectRepository;
    private ArchiveItemReadService itemReadService;
    private ArchivePhysicalTransferService service;

    @BeforeEach
    void setUp() {
        transferRepository = mock(ArchivePhysicalTransferDataRepository.class);
        itemRepository = mock(ArchivePhysicalTransferItemDataRepository.class);
        physicalObjectRepository = mock(ArchivePhysicalObjectDataRepository.class);
        itemReadService = mock(ArchiveItemReadService.class);
        OrganizationDepartmentService departmentService = mock(OrganizationDepartmentService.class);
        AuthorizationPermissionService permissionService =
                mock(AuthorizationPermissionService.class);
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(permissionService.hasPermission(9L, "archive:item:read")).thenReturn(true);
        service =
                new ArchivePhysicalTransferService(
                        transferRepository,
                        itemRepository,
                        physicalObjectRepository,
                        itemReadService,
                        mock(ArchiveVolumeService.class),
                        departmentService,
                        permissionService,
                        Clock.fixed(Instant.parse("2026-08-11T01:02:03Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("提交批次时快照清单并将实物改为待接收")
    void createShouldSnapshotItemsAndMarkPendingReceipt() {
        ArchivePhysicalObject physicalObject = departmentPhysicalObject();
        when(transferRepository.findByTransferNo("TR-001")).thenReturn(Optional.empty());
        when(physicalObjectRepository.findById(51L)).thenReturn(Optional.of(physicalObject));
        when(transferRepository.insert(any(ArchivePhysicalTransfer.class)))
                .thenAnswer(
                        invocation -> {
                            ArchivePhysicalTransfer transfer = invocation.getArgument(0);
                            transfer.setId(10L);
                            return transfer;
                        });
        when(itemRepository.insert(any(ArchivePhysicalTransferItem.class)))
                .thenAnswer(
                        invocation -> {
                            ArchivePhysicalTransferItem item = invocation.getArgument(0);
                            item.setId(20L);
                            return item;
                        });

        var response =
                service.create(
                        new CreateArchivePhysicalTransferRequest(
                                "TR-001", 7L, List.of(51L), "纸质档案移交"),
                        9L);

        assertThat(response.status()).isEqualTo(ArchivePhysicalTransferStatus.PENDING_RECEIPT);
        assertThat(response.items())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.archiveItemId()).isEqualTo(31L);
                            assertThat(item.barcode()).isEqualTo("B001");
                        });
        assertThat(physicalObject.getCustodyStatus())
                .isEqualTo(ArchivePhysicalCustodyStatus.PENDING_RECEIPT);
        verify(physicalObjectRepository).update(physicalObject);
    }

    @Test
    @DisplayName("接收室藏档案实物只改变实物保管状态")
    void acceptShouldKeepArchiveOwnerAndMoveCustodyToArchiveRoom() {
        ArchivePhysicalTransfer transfer = pendingTransfer();
        ArchivePhysicalTransferItem item = activeItem();
        ArchivePhysicalObject physicalObject = departmentPhysicalObject();
        physicalObject.setCustodyStatus(ArchivePhysicalCustodyStatus.PENDING_RECEIPT);
        when(transferRepository.findById(10L)).thenReturn(Optional.of(transfer));
        when(itemRepository.findByTransferId(10L)).thenReturn(List.of(item));
        when(physicalObjectRepository.findById(51L)).thenReturn(Optional.of(physicalObject));

        var response = service.accept(10L, new AcceptArchivePhysicalTransferRequest("清点一致"), 9L);

        assertThat(response.status()).isEqualTo(ArchivePhysicalTransferStatus.ACCEPTED);
        assertThat(response.receiptNote()).isEqualTo("清点一致");
        assertThat(physicalObject.getArchiveItemId()).isEqualTo(31L);
        assertThat(physicalObject.getCustodyStatus())
                .isEqualTo(ArchivePhysicalCustodyStatus.ARCHIVE_ROOM_CUSTODY);
        assertThat(item.isActiveFlag()).isFalse();
        verify(transferRepository).update(transfer);
        verify(physicalObjectRepository).update(physicalObject);
        verify(itemRepository).update(item);
    }

    @Test
    @DisplayName("退回批次时实物恢复为业务部门保管")
    void rejectShouldReturnCustodyToDepartment() {
        ArchivePhysicalTransfer transfer = pendingTransfer();
        ArchivePhysicalTransferItem item = activeItem();
        ArchivePhysicalObject physicalObject = departmentPhysicalObject();
        physicalObject.setCustodyStatus(ArchivePhysicalCustodyStatus.PENDING_RECEIPT);
        when(transferRepository.findById(10L)).thenReturn(Optional.of(transfer));
        when(itemRepository.findByTransferId(10L)).thenReturn(List.of(item));
        when(physicalObjectRepository.findById(51L)).thenReturn(Optional.of(physicalObject));

        var response = service.reject(10L, new RejectArchivePhysicalTransferRequest("数量不一致"), 9L);

        assertThat(response.status()).isEqualTo(ArchivePhysicalTransferStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("数量不一致");
        assertThat(physicalObject.getCustodyStatus())
                .isEqualTo(ArchivePhysicalCustodyStatus.DEPARTMENT_CUSTODY);
        assertThat(item.isActiveFlag()).isFalse();
    }

    @Test
    @DisplayName("已完成批次不能重复处理")
    void terminalTransferShouldRejectRepeatedProcessing() {
        ArchivePhysicalTransfer transfer = pendingTransfer();
        transfer.setStatus(ArchivePhysicalTransferStatus.ACCEPTED);
        when(transferRepository.findById(10L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        10L, new RejectArchivePhysicalTransferRequest("重复退回"), 9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已经处理");

        verifyNoInteractions(itemRepository);
        verify(physicalObjectRepository, never()).update(any());
    }

    private ArchivePhysicalObject departmentPhysicalObject() {
        ArchivePhysicalObject physicalObject = new ArchivePhysicalObject();
        physicalObject.setId(51L);
        physicalObject.setArchiveItemId(31L);
        physicalObject.setBarcode("B001");
        physicalObject.setCarrierType("PAPER");
        physicalObject.setQuantity(BigDecimal.ONE);
        physicalObject.setQuantityUnit("卷");
        physicalObject.setConditionNote("完好");
        physicalObject.setCustodyStatus(ArchivePhysicalCustodyStatus.DEPARTMENT_CUSTODY);
        return physicalObject;
    }

    private ArchivePhysicalTransfer pendingTransfer() {
        ArchivePhysicalTransfer transfer = new ArchivePhysicalTransfer();
        transfer.setId(10L);
        transfer.setTransferNo("TR-001");
        transfer.setSourceDepartmentId(7L);
        transfer.setStatus(ArchivePhysicalTransferStatus.PENDING_RECEIPT);
        transfer.setSubmittedBy(9L);
        transfer.setSubmittedAt(java.time.LocalDateTime.of(2026, 8, 11, 1, 2, 3));
        return transfer;
    }

    private ArchivePhysicalTransferItem activeItem() {
        ArchivePhysicalTransferItem item = new ArchivePhysicalTransferItem();
        item.setId(20L);
        item.setTransferId(10L);
        item.setPhysicalObjectId(51L);
        item.setArchiveItemId(31L);
        item.setBarcodeSnapshot("B001");
        item.setActiveFlag(true);
        return item;
    }
}
