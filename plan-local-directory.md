# 本地目录分组 · Subagent 执行计划

> 自包含执行文档。每个 Phase 由独立 subagent 在其全新上下文中执行, 只读本文件对应章节与必要的 reference, 不要跨 Phase 改动。
> 仓库: `dasoops/legado-with-MD3` (本地阅读精简 fork)。工作目录即仓库根目录。

## 0. 背景与已确认决策

需求: 书架顶部标签新增若干 **本地目录分组**, 每个绑定一个存储目录文件夹; 选中后按目录树浏览 (支持嵌套文件夹), 点击书籍导入书架并打开阅读; 已入库文件显示阅读进度。同时清理线上遗留的恒空分组与失效刷新链路。

已确认决策:
1. **数据落点: 方案 A** — `BookGroup` 新增列 `localDirectoryUri`, DB 105 → 106, 手写 Migration (因需额外 DELETE 恒空系统分组, 不能用 AutoMigration)。
2. **点击行为**: `LocalBook.importFiles` 入库后打开; 已在书架的 `findAndRebind` 后打开。
3. **文件范围**: 文件夹恒显示; 文件仅 `bookFileRegex` (txt/epub/mobi/pdf/umd), **不含压缩包**; 隐藏 `.` 开头项。
4. **视图能力**: 搜索文件名、排序 (名称/时间/大小)、面包屑 + 返回、实时刷新 (ContentObserver + 兜底轮询), **无下拉刷新**。
5. **进度显示**: 仅显示阅读进度 (`(durChapterIndex+1)/totalChapterNum` + 章节名); 不显示是否在书架。
6. **精简范围**: 移除恒空系统分组 (`IdAudio/IdNetNone/IdManga/IdError`) 与失效刷新链路。保留 `IdAll/IdLocal/IdLocalNone/IdText/IdReading/IdUnread/IdReadFinished*`。

## 1. 全局约定

- 语言: 代码注释与提交信息用中文; 注释只写 WHY, 不复述代码。
- 文本改动后运行 `git diff --check`。
- 构建命令 (Linux/WSL, 用 `./gradlew`; Windows 用 `.\gradlew.bat`):
  - 快速编译: `./gradlew :app:compileAppDebugKotlin --no-configuration-cache`
  - 全量验证: `./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache`
- `verifyConfigArchitecture` 是 `compile*`/`assemble*` 的前置门禁: **UI/ViewModel 不得直连 `appDb.*Dao`、旧偏好 API**; 必须经 Gateway/Repository。新增 Gateway 需在 `di/appModule.kt` 显式绑定接口→实现。
- Feature-first 命名: 新包 `io.legado.app.feature.localdirectory`; Koin 用 `single { }` / `viewModelOf`。
- 不要改动无关代码; 每个 Phase 独立编译通过再交付。

### 共享接口约定 (各 Phase 必须一致)

```kotlin
// BookGroup 实体 (Phase 1)
var localDirectoryUri: String? = null
val isLocalDirectory: Boolean get() = !localDirectoryUri.isNullOrBlank()

// BookGroupUi (Phase 1)
val isLocalDirectory: Boolean
val localDirectoryUri: String?

// 领域模型 (Phase 2, 放 io.legado.app.domain.model)
data class LocalDirectoryEntry(
    val uri: String, val name: String, val isDir: Boolean,
    val size: Long, val lastModified: Long,
)
data class LocalBookProgress(
    val bookUrl: String, val durChapterIndex: Int,
    val totalChapterNum: Int, val durChapterTitle: String?,
)

// Gateway (Phase 2, 放 io.legado.app.domain.gateway)
interface LocalDirectoryGateway {
    suspend fun rootDocument(treeUri: String): LocalDirectoryEntry?
    suspend fun listChildren(dirUri: String): List<LocalDirectoryEntry>
    fun flowLocalBookProgress(): Flow<Map<String, LocalBookProgress>>  // key = originName
}
```

---

## Phase 1 — 数据模型与迁移

前置: 无。产出一个可编译、可验证迁移的提交。

### 改动

1. `app/src/main/java/io/legado/app/data/entities/BookGroup.kt`
   - 新增字段 (不要加 `@ColumnInfo(defaultValue=...)`, 保持 nullable 无默认):
     ```kotlin
     var localDirectoryUri: String? = null
     ```
   - 加派生属性 `val isLocalDirectory: Boolean get() = !localDirectoryUri.isNullOrBlank()`。
   - 更新 `equals()` 与 `hashCode()` 视现有实现决定是否纳入新字段 (建议纳入, 保持数据类语义)。

2. `app/src/main/java/io/legado/app/data/AppDatabase.kt`
   - `version = 105` 改为 `106`。
   - 不要新增 105→106 的 AutoMigration。

3. `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
   - 新增手写迁移并加入 `migrations` 数组:
     ```kotlin
     private val migration_105_106 = object : Migration(105, 106) {
         override fun migrate(db: SupportSQLiteDatabase) {
             db.execSQL("ALTER TABLE book_groups ADD COLUMN localDirectoryUri TEXT")
             // 清理 fork 早期写入的恒空线上系统分组 (音频/网络未分组/漫画/更新失败)
             db.execSQL("DELETE FROM book_groups WHERE groupId IN (-3, -4, -7, -11)")
         }
     }
     ```
   - 注意 `migrations` 数组当前类型是 `Array<Migration>`, 直接加入。

4. `app/src/main/java/io/legado/app/domain/model/BookGroupMutation.kt`
   - `NewBookGroup` 与 `BookGroupUpdate` 各加 `val localDirectoryUri: String? = null`。

5. `app/src/main/java/io/legado/app/data/repository/BookGroupMutationRepository.kt`
   - `addGroup`: `BookGroup(...)` 增加 `localDirectoryUri = group.localDirectoryUri`。
   - `saveGroup` 的 `BookGroupUpdate.toEntity()` 增加 `localDirectoryUri = localDirectoryUri`。

6. `app/src/main/java/io/legado/app/ui/book/group/GroupViewModel.kt`
   - `addGroup(...)` 增参数 `localDirectoryUri: String? = null`, 传入 `NewBookGroup`。
   - `toUpdate()` 增加对应字段。

7. `app/src/main/java/io/legado/app/ui/main/bookshelf/BookGroupUi.kt`
   - `BookGroupUi` 增 `isLocalDirectory: Boolean` 与 `localDirectoryUri: String?`; `toBookGroupUi()` 映射。

### 验证

```bash
./gradlew :app:compileAppDebugKotlin --no-configuration-cache
git diff --check
```

- 编译会生成 `app/schemas/io.legado.app.data.AppDatabase/106.json`, 确认其 `book_groups.createSql` 含 `localDirectoryUri TEXT` (无 DEFAULT), 该文件需随改动提交。
- 迁移正确性: 可在 Phase 1 写一个 Room migration 测试; 若项目已有 migration 测试基础设施则补一条 105→106 用例, 否则至少用 `./gradlew :app:assembleAppDebug` 确认 schema 校验通过 (Room 会在打开库时抛 `IllegalStateException` 若 schema 不匹配, 但编译期 `room` 插件会比对 schema 文件与实体; 编译通过即为一致)。

### 完成标准
- 编译通过; 106.json 生成且正确; 无 Phase 1 范围外的改动。

---

## Phase 2 — 目录浏览 Gateway / Repository

前置: 可独立于 Phase 1 进行 (只依赖既有 `FileDoc`, `bookFileRegex`, `BookImportRepository`)。

### 改动

1. `app/src/main/java/io/legado/app/domain/model/LocalDirectory.kt` (新建)
   - 放第 1 节约定的 `LocalDirectoryEntry` / `LocalBookProgress`。

2. `app/src/main/java/io/legado/app/domain/gateway/LocalDirectoryGateway.kt` (新建)
   - 第 1 节接口。

3. `app/src/main/java/io/legado/app/data/repository/LocalDirectoryRepository.kt` (新建)
   - 实现 `LocalDirectoryGateway`, 构造注入 `BookImportRepository`。
   - `rootDocument`: `treeUri.toUri()` → `FileDoc.fromUri(uri, true)` (try/catch 返回 null)。
   - `listChildren(dirUri)`:
     - `FileDoc.fromUri(dirUri.toUri(), true).list { item -> item.isDir || item.name.matches(AppPattern.bookFileRegex) }`
     - 过滤 `.` 开头; 映射为 `LocalDirectoryEntry`。
   - `flowLocalBookProgress`: `bookImportRepository.flowLocalBooks()` map 成 `originName -> LocalBookProgress`.
   - 目录读取必须 `withContext(Dispatchers.IO)`。
   - 平台细节 (DocumentFile/FileDoc/ContentResolver) 只在本 repository 出现。

4. `app/src/main/java/io/legado/app/di/appModule.kt`
   - 增加 `single<LocalDirectoryGateway> { LocalDirectoryRepository(get()) }` (按文件内既有风格)。

### 验证
```bash
./gradlew :app:compileAppDebugKotlin verifyConfigArchitecture --no-configuration-cache
git diff --check
```

### 完成标准
- Gateway 被 Koin 显式绑定; UI 层无平台调用; 编译与门禁通过。

---

## Phase 3 — Feature 目录视图

前置: Phase 2。本 Phase 不依赖 Phase 1 (rootUri 以 String 传入)。

### 新建包 `io.legado.app.feature.localdirectory`

1. `LocalDirectoryContract.kt`
   ```kotlin
   @Stable data class LocalDirectoryItem(
       val entry: LocalDirectoryEntry,
       val progress: LocalBookProgress?,   // 仅文件且已入库
   )
   @Stable data class LocalDirectoryUiState(
       val rootUri: String = "",
       val pathNames: ImmutableList<String> = persistentListOf(),
       val items: ImmutableList<LocalDirectoryItem> = persistentListOf(),
       override val selectedIds: ImmutableSet<Any> = persistentSetOf(),
       override val searchKey: String = "",
       override val isSearch: Boolean = false,
       override val isLoading: Boolean = false,
       val sort: Int = 0,
       val isUnavailable: Boolean = false,
   ) : ListUiState<LocalDirectoryItem>

   sealed interface LocalDirectoryIntent {
       data object Initialize; data object Refresh
       data object NavigateBack
       data class NavigateToLevel(val index: Int)
       data class EnterDir(val item: LocalDirectoryItem)
       data class ItemClick(val item: LocalDirectoryItem)
       data class SearchToggle(val enabled: Boolean)
       data class SearchQueryChange(val query: String)
       data class SortChange(val sort: Int)
   }
   sealed interface LocalDirectoryEffect {
       data class OpenBook(val book: Book) : LocalDirectoryEffect
       data class ShowToast(val message: String) : LocalDirectoryEffect
   }
   ```
   (`ListUiState` 在 `io.legado.app.ui.widget.components.list`; 若字段不方便可简化, 但需能传入 `ListScaffold`。)

2. `LocalDirectoryViewModel.kt`
   - 直接继承 `ViewModel`; 构造 `application: Application, gateway: LocalDirectoryGateway, importRepository: BookImportRepository`; 根目录来自 `SavedStateHandle` 或构造参数 `rootUri` (由 BookshelfScreen 以 `koinViewModel(key = "localDir:$groupId", parameters = { parametersOf(uri) })` 注入)。
   - 私有 `MutableStateFlow<LocalDirectoryUiState>` + `MutableSharedFlow<LocalDirectoryEffect>(extraBufferCapacity = 16)`; 对外只读。
   - 路径栈 `List<String>` (存 name) + 当前 `FileDoc`/uri 栈, 用于回退/跳级。
   - `Initialize`/`Refresh` → 读根 + 列目录; uri 无效时 `isUnavailable = true`。
   - 实时刷新: 在 `IO` 协程中注册 `ContentObserver` 到
     `DocumentsContract.buildChildDocumentsUriUsingTree(currentUri, getDocumentId(currentUri))`
     同时挂根目录; 变化即重列。`onCleared` 取消。另加可见期兜底轮询 (例如 3s), 防止 provider 不通知。
   - 排序: 0 名称 (文件夹优先, `AlphanumComparator`), 1 大小降序, 2 修改时间降序。
   - 搜索: 按名称过滤当前层 items。
   - `ItemClick`: 目录 → `EnterDir`; 文件 → 若 `progress != null` 用 `BookDao`/repository 找 Book? 不允许直连 DAO: 经 `BookImportRepository.findAndRebind(name, uri.toString())`; 为空则 `LocalBook.importFiles(listOf(entry.uri.toUri()))` 取首个; 成功 emit `OpenBook`; 失败 `ShowToast`。
   - `LocalBook.importFiles` 与 `ImportBookViewModel` 用法一致, 允许在 ViewModel 调用 (非 DAO/偏好)。

3. `LocalDirectoryScreen.kt`
   - `LocalDirectoryRouteScreen(rootUri, groupId, onOpenBook: (Book) -> Unit, onBackToRootHandled...)` 收集 state/effects; `OpenBook` → `onOpenBook(book)`; Toast → `context.toastOnUi`。
   - 内容基于 `ListScaffold` (`io.legado.app.ui.widget.components.list.ListScaffold`): `title = pathNames.lastOrNull() ?: 本地目录`, `onSearchToggle`, `onSearchQueryChange`, `onBackClick`, `dropDownMenuContent` 放三种排序; `bottomContent` 用 `ImportPathNavigationBar` 同款面包屑 (可复制该私有组件到本 feature 或抽成公共组件, 倾向复制最小实现, 不改旧文件)。
   - 列表项: 文件夹 -> 文件夹图标; 文件 -> 扩展名标签 + 大小 + 时间; 已入库文件在副标题追加 `durChapterTitle` 与 `x/y`。
   - `isUnavailable` 时显示 `EmptyMessage`/提示 "目录不可用, 请重新选择"。
   - 不支持多选/删除 (不传 `selectionActions`, `onAddClick` 留空)。

### 验证
```bash
./gradlew :app:compileAppDebugKotlin verifyConfigArchitecture --no-configuration-cache
git diff --check
```

### 完成标准
- Feature 可独立编译; 无 UI 直连 DAO/偏好; 效果在 Phase 4 接入书架后手工验证。

---

## Phase 4 — 书架集成

前置: Phase 1 + 2 + 3。

### 改动 `app/src/main/java/io/legado/app/ui/main/bookshelf/`

1. `BookShelfItem.kt`: 增加实体映射, 供目录视图复用现有导航回调:
   ```kotlin
   fun Book.toShelfItem(): BookShelfItem = BookShelfItem(
       bookUrl, name, author, origin, originName, coverUrl, customCoverUrl,
       durChapterTitle, durChapterTime, durChapterPos, latestChapterTitle,
       latestChapterTime, lastCheckCount, totalChapterNum, durChapterIndex,
       type, group, order, canUpdate, intro, kind, customTag, wordCount,
   )
   ```

2. `BookshelfScreen.kt`
   - `HorizontalPager` 的 page 内容按 `group.isLocalDirectory` 分支:
     - 目录组: 渲染 `LocalDirectoryRouteScreen(rootUri = group.localDirectoryUri!!, groupId = group.groupId, onOpenBook = { onBookClick(it.toShelfItem(), null) })`。View key 用 `koinViewModel(key = "localDir:${group.groupId}", parameters = { parametersOf(uri) })` 时注意在 composable 内取用。
     - 普通组: 原 `BookshelfPage`。
   - 目录组页面禁用下拉刷新 (`AppPullToRefresh(enabled = !isLocalDirectoryPage ...)`)。
   - 返回键: 目录组且当前在子目录时, 先退子目录 (通过向 `LocalDirectoryViewModel` 发 Back); 到根再走系统返回。可在 `BookshelfScreen` 的 BackHandler 链中根据当前 page 是否为目录组与目录深度处理。
   - 编辑模式/多选/拖拽仅对普通组生效。

3. `BookshelfViewModel.kt`
   - `computeHiddenGroupIds`: 跳过 `group.isLocalDirectory == true` (目录组永不因空而隐藏)。
   - `groupPreviewsFlow`: 对 `isLocalDirectory` 的组不请求 DB 预览/计数 (避免显示 0/空封面)。
   - `allGroupBooksImmutableFlow`/`visibleGroupBooks`: 目录组可留空 (其 page 不读该 map)。

### 验证
```bash
./gradlew :app:compileAppDebugKotlin verifyConfigArchitecture --no-configuration-cache
git diff --check
```

### 完成标准
- 混入目录组后, 普通组分页/搜索/排序/标签位置不受影响; 目录组不显示 DB 计数; 编译+门禁通过。

---

## Phase 5 — 分组管理入口

前置: Phase 1。

### 改动

1. `app/src/main/java/io/legado/app/ui/main/bookshelf/GroupManageSheet.kt`
   - 「添加分组」下拉 (`RoundDropdownMenu`) 增加项「添加本地目录分组」。
   - 点击后用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())` 选目录; 回调里:
     - `uri.takePersistablePermissionSafely(context)` (复用 `io.legado.app.utils.takePersistablePermissionSafely`)。
     - 默认名称: `DocumentFile.fromTreeUri(context, uri)?.name` 为空时取 `DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').substringAfter(':')`, 再为空用 "本地目录"。
     - 调 `GroupViewModel.addGroup(groupName = defaultName, bookSort = -1, enableRefresh = false, isPrivate = false, cover = null, pattern = null, localDirectoryUri = uri.toString())`。
   - 注意 launcher 需在 composable 作用域声明。

2. `app/src/main/java/io/legado/app/ui/book/group/GroupEditSheet.kt` (`GroupEditContent`)
   - 当 `group?.isLocalDirectory == true`:
     - 显示当前目录 (只读文本) + 「重新选择目录」按钮 (`OpenDocumentTree`), 选择后更新本地 `localDirectoryUri` 状态。
     - 隐藏: 排序下拉、`allow_drop_down_refresh` 开关、标签匹配规则、私有开关 (目录组无意义)。
     - `saveGroup(bookGroup = group.copy(..., localDirectoryUri = newUri))`。
   - 新建普通分组路径不变 (`localDirectoryUri = null`)。

3. 字符串: 在 `app/src/main/res/values/strings.xml` 与 `values-zh-rCN`、`zh-rTW`、`zh-rHK` 增加:
   `add_local_directory_group`、`reselect_directory`、`directory_unavailable`、`local_directory`。英文与简中必填, 繁中可回退到简中则按现有惯例补齐。

### 验证
```bash
./gradlew :app:compileAppDebugKotlin lintAppDebug --no-configuration-cache
git diff --check
```

### 完成标准
- 能从分组管理创建/编辑/删除目录组; 默认名可编辑; 编译/lint 通过。

---

## Phase 6 — 书架精简

前置: Phase 4 (本 Phase 与 Phase 4 都改 `BookshelfScreen`/`BookshelfViewModel`, 必须串行)。

### 6.1 移除失效刷新链路

`app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt` 删除:
- `updateQueueLock`/`waitUpTocBooks`/`onUpTocBooks`/`updatingBooksFlow`/`upBooksCountFlow`/`upTocJob` 及方法 `addToWaitUp/startUpTocJobLocked/pollWaitUpBookUrl/markBookUpdateStarted/markBookUpdateFinished/onUpTocBooksSnapshot/finishUpTocJob/completeRefreshIfIdle/updateToc/postUpBooksCount/enqueueTocUpdate`。
- `isRefreshingFlow` 及 `refreshBooks`; `upAllBookToc`/`upToc`; `RefreshAll`/`RefreshBooks`/`RefreshToc` intent 分支。
- `init` 中 `FlowEventBus.with<Unit>(EventBus.UP_ALL_BOOK_TOC)` 收集与 `isInitialLoadingFlow.filter{!it}` 的 `upAllBookToc` 调用; `settings` 收集里的 `postUpBooksCount()`。

`BookshelfUiState.kt`: 删除 `RefreshBooks`/`RefreshToc`/`RefreshAll`、`upBooksCount`/`updatingBooks`。
`BookshelfScreen.kt`: 删除菜单「更新目录」项、`AppPullToRefresh` 包装 (保留 `scrollBehavior` 用法)、`isUpdating` 相关展示。
`MainActivity.kt`: 删除 `viewModel.upAllBookToc()` 调用 (约 line 165)。
`MainViewModel.kt`: 删除 `upAllBookToc()`。
`constant/EventBus.kt`: 删除 `UP_ALL_BOOK_TOC`; `UP_BOOKSHELF` 若无其它引用一并删除 (先 grep 确认)。
`ui/book/group/GroupEditSheet.kt`: 删除 `allow_drop_down_refresh` 开关与 `enableRefresh` 状态 (实体列保留, 不再使用)。
保留阅读器内 `MenuRefreshAll`/`RefreshAllChapters` (本地章节重解析, 有效)。

### 6.2 移除恒空系统分组

`app/src/main/java/io/legado/app/data/AppDatabase.kt` (`onOpen`): 删除 `IdAudio/IdNetNone/IdManga/IdError` 的建组 SQL 块。
`app/src/main/java/io/legado/app/data/dao/BookDao.kt`:
- `flowByGroup`、`flowBookShelfByGroup` 删除对应 `when` 分支。
- `flowSystemGroupCounts` 删除对应 `UNION ALL`。
- 删除仅被上述引用的 `@Query` 方法 (`flowAudio/flowNetNoGroup/flowManga/flowUpdateError/flowBookShelfAudio/flowBookShelfNetNoGroup/flowBookShelfManga/flowBookShelfUpdateError`) — 先用 grep 确认无其它调用方再删。
`app/src/main/java/io/legado/app/data/entities/BookGroup.kt`: 删除 `IdAudio/IdNetNone/IdManga/IdError` 常量与 `getManageName` 中对应分支。
保留 `IdAll/IdLocal/IdLocalNone/IdText/IdRoot/IdReading/IdUnread/IdReadFinished*`。
`verifyConfigArchitecture` 基线无需改动 (未触及 DAO/偏好基线)。

### 验证
```bash
./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
git diff --check
```

### 完成标准
- 无残留引用; 全量验证通过。

---

## 8. 手工验收 (全量完成后)

1. 设置/引导定义存储目录; 分组管理 → 添加本地目录分组 → 选目录 → 标签显示目录名。
2. 再建第二个目录组; 两标签并存。
3. 进入嵌套子目录 → 面包屑跳级/返回; 搜索命中当前层; 三种排序生效。
4. 点未入库文件 → 自动入库并打开阅读; 再次点击直接打开。
5. 返回书架, 该文件在目录组内显示阅读进度 (章节名 + x/y)。
6. 外部新增/删除文件 → 目录视图自动刷新, 无需下拉。
7. 开启「隐藏空分组」→ 目录组仍显示。
8. 重启 → 标签位置记忆; 删除目录组 → 列表与抽屉同步移除, 无崩溃。
9. 105 库升级 106: 原分组保留, 音频/网络/漫画/更新失败分组消失。
10. WebDAV 备份→恢复: 目录组存在但 URI 失效 → 显示「目录不可用」且可重选。

## 9. Subagent 分工与提交切分

| 提交 | Phase | 依赖 | 并行性 |
|---|---|---|---|
| 1 | Phase 1 | 无 | 串行起点 |
| 2 | Phase 2 | 无 (可与 1 并行) | 独立新建文件, 但 Koin 文件可能与 1 无冲突 |
| 3 | Phase 3 | Phase 2 | Phase 2 后 |
| 4 | Phase 4 | 1+2+3 | 串行 |
| 5 | Phase 5 | 1 | 可在 1 后与 2/3 并行 |
| 6 | Phase 6 | 4 | 串行收尾 |

每个 subagent 收到: 「读 `plan-local-directory.md` 的 Phase N 章节, 仅实现该 Phase, 完成后运行该 Phase 验证命令, 报告改动文件/命令/结果/未决问题」。不得修改其他 Phase 的文件。

## 10. 风险与回滚

- **迁移是唯一数据风险点**: 新增列 + 删除分组行同一迁移。回滚需降级迁移 (删除列 SQLite 需重建表); 出问题优先用 `noR8` 变体 + 全新安装排障。
- **schema 文件必须提交**: `app/schemas/.../106.json` 与实体一致, 否则 Room 校验失败。
- **SAF 通知差异**: 部分 provider 不触发 `ContentObserver`, 兜底轮询不可省。
- **分页索引耦合**: 目录组混入 `HorizontalPager` 后需复核 `selectedGroupIndex`/`saveTabPosition`。
- **门禁**: 任何 UI 直连 DAO/偏好会在 `verifyConfigArchitecture` 直接失败。
- **备份语义**: 目录 URI 随 `book_groups` 备份到其他设备属预期代价, 用「目录不可用 + 重选」兜底。

---

# Revision 2 — 改为「自动入库 + 文件夹分层」

> Phase 1-6 已完成并提交 (commit 94d9bbc0d)。用户实测后要求改方向。以下为覆盖式修订, 实现时以本节为准; 旧 Phase 2/3/4 的文件浏览实现全部作废。

## R0. 新模型与用户反馈

- **模型**: 目录组不再即时浏览文件, 而是把目录内书籍**递归导入书架并归入该组**; 标签内按真实文件夹分层浏览这些已入库书籍。
- 用户反馈:
  1. 旧实现多级目录不生效 (点击只改面包屑, 内容不变)。根因: `LocalDirectoryRepository.listChildren` 用 `FileDoc.fromUri(childDocUri, true)` 走了 `DocumentFile.fromTreeUri`, 会退回树的**根文档**, 丢失子文档 id, 导致永远重列根目录。
  2. 移除目录 tab 内的 返回/标题/搜索/排序; 搜索走书架全局搜索。
  3. 书籍用统一卡片展示: 封面 + 标题 + x章未读; 不要大小/修改时间。
  4. 允许"添加标签时导入全部书籍, 刷新时重扫"。

## R1. 数据/导入层 (Phase R1, 无 UI)

### 接口 (替换旧 `LocalDirectoryGateway`)

```kotlin
// io.legado.app.domain.gateway.LocalDirectoryGateway
interface LocalDirectoryGateway {
    /** 目录显示名 (最后一级); 无法解析返回 null */
    suspend fun directoryName(rootUri: String): String?
    /** 递归扫描 rootUri 内书籍并导入书架、归入 groupId; 返回本次处理的书籍数 */
    suspend fun importDirectoryToGroup(groupId: Long, rootUri: String): Int
    /** bookUrl 相对于 rootUri 的目录层级 (不含文件名); 解析失败返回空 */
    fun relativeDirectory(rootUri: String, bookUrl: String): List<String>
}
```

- 删除 `LocalDirectoryEntry`/`LocalBookProgress` (`domain/model/LocalDirectory.kt`) 与旧 gateway 三方法, 确认无残留引用后删文件。
- `LocalDirectoryRepository` 重写:
  - 构造注入 `BookRepository` (取书/更新) 或直接用 `appDb` (与 `BookImportRepository` 一致)。用 `BookRepository.getBook/insert/update`。
  - `directoryName`: content scheme 用 `DocumentFile.fromTreeUri`; file scheme 用 `File(path).name`; 兜底 `getTreeDocumentId(...).substringAfterLast('/').substringAfter(':')`。
  - `importDirectoryToGroup`: 用 `FileDoc` 构造根目录 (content 用 `fromUri(uri,true)` 得到根 tree; file 用 `File(path)`), BFS 扫描匹配 `AppPattern.bookFileRegex` 的文件 (排除压缩包、隐藏项), 对每个: 已存在且 `(book.group and groupId) != 0L` 则跳过; 否则 `LocalBook.importFile(uri)` 后把 `book.group` 置为该组并 `book.save()`。全程 `Dispatchers.IO`, 单项失败 `runCatching` 跳过。返回处理计数。
  - `relativeDirectory`: content scheme 用 `DocumentsContract.getTreeDocumentId(rootUri)` 与 `getDocumentId(bookUrl.toUri())`, `removePrefix` 后 `split('/').dropLast(1)`; file scheme 用 `File(...).relativeTo(root).path` 同理。失败返回空。

## R2. Feature 重写 (Phase R2)

- 删除旧的 `ContentObserver` 文件浏览逻辑; 改为读取 DB 中的组内书籍并构建虚拟文件夹树。
- `LocalDirectoryContract.kt`:
  ```kotlin
  @Stable data class DirectoryBook(val ui: BookUiItem, val dir: ImmutableList<String>)
  sealed interface LocalDirectoryNode {
      data class Folder(val name: String) : LocalDirectoryNode
      data class Book(val book: DirectoryBook) : LocalDirectoryNode
  }
  @Stable data class LocalDirectoryUiState(
      val path: ImmutableList<String> = persistentListOf(),
      val pathNames: ImmutableList<String> = persistentListOf(), // 根名 + path
      val nodes: ImmutableList<LocalDirectoryNode> = persistentListOf(),
      val isLoading: Boolean = false,
      val isUnavailable: Boolean = false,
  )
  ```
  Intent: Initialize/Refresh/EnterFolder(name)/NavigateToLevel(index)/NavigateBack/SearchChange(key, isSearch)。
  Effect: OpenBook(bookUrl: String)、ShowToast(String)。
- `LocalDirectoryViewModel(application, gateway, bookRepository, groupId: Long, rootUri: String)`:
  - `Initialize`: 先 `gateway.importDirectoryToGroup(groupId, rootUri)` (重扫); 失败/空目录置 `isUnavailable`; 然后基于书籍流构建树。
  - 书籍流: `bookRepository.flowBookShelfByGroup(groupId).map { it.map { b -> b.toUiItem() } }`; 对每本用 `gateway.relativeDirectory(rootUri, book.bookUrl)` 得到 `dir` (在 Default 调度计算一次并缓存)。
  - 树: 当前 `path` 下, 子文件夹 = 各书 dir 在 path 前缀后多出的第一段去重; 本级书籍 = dir == path。文件夹按名称排序在前, 书籍按名称排序在后。
  - `isSearch`: 忽略 path, 扁平展示按 `searchKey` 过滤 name/author 的书籍。
  - 不直连 DAO/偏好; 经 `BookRepository` 与 `LocalDirectoryGateway`。
- `LocalDirectoryScreen.kt` (content-only, 不再用 `ListScaffold`):
  - 顶部一行: 面包屑 (根名 + path, 可点击跳级) + 右侧刷新按钮 (`Refresh` intent)。无返回/标题/搜索/排序。
  - `BackHandler(enabled = path.isNotEmpty())` 上溯; 搜索态不拦截。
  - 内容: `LazyVerticalGrid`, 网格/列表按 `BookshelfSettings` 的 portrait/landscape 配置解析 (与 `BookshelfPage` 一致)。
    - 文件夹: 用 `BookshelfGridItem`/`BookshelfListItem` 展示 (cover 传 folder 图标), 点击 `EnterFolder`。
    - 书籍: 复用 public `BookItem` (settings、customTagColors、layoutMode、gridStyle、title 等), 点击 `OpenBook`。
  - 搜索态: 扁平展示过滤后的书籍。
  - 签名 (供 Phase R3 调用):
    ```kotlin
    @Composable fun LocalDirectoryRouteScreen(
        groupId: Long, rootUri: String,
        settings: BookshelfSettings, customTagColors: ImmutableList<TagColorPair>,
        searchKey: String, isSearch: Boolean,
        contentPadding: PaddingValues,
        onOpenBook: (BookShelfItem, String?) -> Unit,
        modifier: Modifier = Modifier,
    )
    ```
- Koin: `viewModel { (groupId: Long, rootUri: String) -> LocalDirectoryViewModel(get(), get(), get(), groupId, rootUri) }`。

## R3. 书架集成与入口 (Phase R3)

- `BookshelfScreen.kt` 目录组 page 改为:
  ```kotlin
  LocalDirectoryRouteScreen(
      groupId = group.groupId,
      rootUri = localDirectoryUri,
      settings = uiState.settings,
      customTagColors = if (uiState.enableCustomTagColors) uiState.customTagColors else persistentListOf(),
      searchKey = uiState.searchKey,
      isSearch = uiState.isSearch,
      contentPadding = paddingValues,
      onOpenBook = onBookClick,
  )
  ```
  不再包 `AppPullToRefresh`, 不做内容级编辑/多选。
- `BookshelfViewModel`: 目录组的 `allGroupBooks` 仍走 `flowBookShelfByGroup` (现在有书了); 目录组**不参与**空格隐藏 (已实现) 与预览计数 (已实现) 保持。
  注意: 全局搜索时 `visibleGroupBooks[groupId]` 会过滤; `LocalDirectoryViewModel` 自行按 `searchKey` 过滤, 二者无需强一致。
- `GroupManageSheet`: 添加目录组时仍只创建分组 + 保存 URI; **不再**在创建时同步扫描 (导入改由打开 tab / 点刷新时触发), 避免 sheet 关闭导致协程取消。
- 字符串: 移除不再使用的 `add_local_directory_group`(仍用)/`directory_unavailable` 保留; 新增刷新按钮 contentDescription 复用现有 `R.string.refresh`。

## R4. 验证

- `cmd.exe /c "cd /d C:\dasoops\workSpace\jvm\legado-with-MD3 && gradlew.bat :app:compileAppDebugKotlin verifyConfigArchitecture --no-configuration-cache"`
- 手工: 添加目录组 → 打开 tab 触发扫描导入 → 面包屑分层进入 → 文件夹/书籍卡片正确 → 全局搜索命中 → 点书打开 → 外部新增文件后点刷新出现。
