package github.luckygc.am.module.archive.item.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.data.page.PageRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.security.AuthenticatedUser;
import github.luckygc.am.infrastructure.web.CursorPageArgumentResolver;
import github.luckygc.am.module.archive.item.service.ArchiveItemLockService;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService;
import github.luckygc.am.module.archive.item.service.ArchiveItemRelationService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService;
import github.luckygc.am.module.archive.item.service.ArchiveItemSearchService.SearchArchiveItemsRequest;
import github.luckygc.am.module.archive.item.service.ArchiveItemService;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.ReassignArchiveItemFondsRequest;

@DisplayName("档案条目 HTTP 入口")
class ArchiveItemControllerTests {

    private final ArchiveItemService archiveItemService = mock(ArchiveItemService.class);
    private final ArchiveItemSearchService archiveItemQueryService =
            mock(ArchiveItemSearchService.class);
    private final ArchiveItemReadService archiveItemReadService =
            mock(ArchiveItemReadService.class);
    private final ArchiveItemRelationService archiveItemRelationService =
            mock(ArchiveItemRelationService.class);
    private final ArchiveItemLockService archiveItemLockService =
            mock(ArchiveItemLockService.class);
    private final ArchiveItemController controller =
            new ArchiveItemController(
                    archiveItemService,
                    archiveItemQueryService,
                    archiveItemReadService,
                    archiveItemRelationService,
                    archiveItemLockService);

    @Test
    @DisplayName("档案概览从 URL 接收分页大小和全宗筛选")
    void listItemsShouldBindPageControls() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new CursorPageArgumentResolver())
                        .build();

        mvc.perform(
                        get("/archive-items")
                                .param("fondsCode", "F001")
                                .param("limit", "2")
                                .principal(authentication(9L)))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(archiveItemQueryService)
                .listItems(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq("F001"),
                        org.mockito.ArgumentMatchers.eq(9L),
                        page.capture());
        org.assertj.core.api.Assertions.assertThat(page.getValue().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("档案条目局部更新只接受 Merge Patch")
    void patchItemRequiresMergePatchMediaType() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        new AuthenticatedUser() {
                            @Override
                            public Long id() {
                                return 9L;
                            }

                            @Override
                            public String displayName() {
                                return "测试用户";
                            }
                        },
                        null);

        mvc.perform(
                        patch("/archive-items/7")
                                .principal(authentication)
                                .contentType("application/json")
                                .content("{\"item\":{\"archiveNo\":\"A-002\"}}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(archiveItemService);
        mvc.perform(
                        patch("/archive-items/7")
                                .principal(authentication)
                                .contentType("application/merge-patch+json")
                                .content("{\"item\":{\"archiveNo\":\"A-002\"}}"))
                .andExpect(status().isOk());
        verify(archiveItemService)
                .patchItem(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(9L));
    }

    @Test
    @DisplayName("搜索接口从 URL 查询参数接收多列排序")
    void searchItemsShouldBindSortQuery() throws Exception {
        PageRequest page = PageRequest.ofSize(50);
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(
                                new HandlerMethodArgumentResolver() {
                                    @Override
                                    public boolean supportsParameter(MethodParameter parameter) {
                                        return parameter.getParameterType() == PageRequest.class;
                                    }

                                    @Override
                                    public Object resolveArgument(
                                            MethodParameter parameter,
                                            ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            WebDataBinderFactory binderFactory) {
                                        return page;
                                    }
                                })
                        .build();

        mvc.perform(
                        post("/archive-items:search")
                                .param("sort", "-createdAt,+id")
                                .principal(authentication(9L))
                                .contentType("application/json")
                                .content(
                                        "{\"categoryId\":1,\"fondsCode\":\"F001\",\"keyword\":\"合同\",\"volumeId\":12}"))
                .andExpect(status().isOk());

        verify(archiveItemQueryService)
                .searchItems(
                        new SearchArchiveItemsRequest(
                                1L,
                                "F001",
                                "合同",
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new ArchiveItemSearchService.ArchiveItemOrderByRequest(
                                                "createdAt", "DESC"),
                                        new ArchiveItemSearchService.ArchiveItemOrderByRequest(
                                                "id", "ASC")),
                                12L),
                        9L,
                        page);
    }

    @Test
    @DisplayName("关系列表把 depth 和 cursor 分页请求传给服务")
    void listRelationsShouldUseCursorPageAndDepth() {
        Authentication authentication = authentication(9L);
        PageRequest page = PageRequest.ofSize(100);
        @SuppressWarnings("unchecked")
        CursorPageResponse<ArchiveItemRelationService.ArchiveItemRelationResponse> response =
                mock(CursorPageResponse.class);
        when(archiveItemRelationService.listRelations(1L, 2, page, 9L)).thenReturn(response);

        var actual = controller.listRelations(1L, 2, page, authentication);

        org.assertj.core.api.Assertions.assertThat(actual).isSameAs(response);
        verify(archiveItemRelationService).listRelations(1L, 2, page, 9L);
    }

    @Test
    @DisplayName("调整全宗动作转发目标全宗、原因与认证用户")
    void reassignFondsShouldForwardBusinessAction() {
        ReassignArchiveItemFondsRequest request =
                new ReassignArchiveItemFondsRequest("F002", "纠正历史归属");

        controller.reassignItemFonds(10L, request, authentication(9L));

        verify(archiveItemService).reassignFonds(10L, request, 9L);
    }

    private Authentication authentication(Long userId) {
        Authentication authentication = mock(Authentication.class);
        AuthenticatedUser user =
                new AuthenticatedUser() {
                    @Override
                    public Long id() {
                        return userId;
                    }

                    @Override
                    public String displayName() {
                        return "测试用户";
                    }
                };
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }
}
