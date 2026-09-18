# 阅读 API

本 fork 只提供 **本地书籍数据的 Content Provider 导出**，供其他应用读取书架、章节、正文和封面。
在线书源 / RSS / 内嵌 Web 服务及其 HTTP API 已随在线功能一并移除，不再提供。

## Content Provider

实现见 [`ReaderProvider.kt`](app/src/main/java/io/legado/app/api/ReaderProvider.kt)。

- `providerHost` 为 `<applicationId>.readerProvider`，本 fork 即
  `io.github.dasoops.reader.readerProvider`。不同安装包地址不同，请自行替换以下示例。
- 查询结果通过 `Cursor.getString(0)` 取出，内容为 JSON 字符串。

### 插入书籍

创建 `Key="json"` 的 `ContentValues`，内容为 `JSON` 字符串，格式参考
[`Book.kt`](app/src/main/java/io/legado/app/data/entities/Book.kt)。

```
URL = content://providerHost/book/insert
Method = insert
```

### 获取所有书籍

获取应用书架内的所有书籍。

```
URL = content://providerHost/books/query
Method = query
```

### 获取书籍章节列表

获取指定图书的章节列表。

```
URL = content://providerHost/book/chapter/query?url=xxx
Method = query
```

### 刷新目录

重新解析指定本地图书的目录。

```
URL = content://providerHost/book/refreshToc/query?url=xxx
Method = query
```

### 获取书籍内容

获取指定图书第 `index` 章的文本内容。

```
URL = content://providerHost/book/content/query?url=xxx&index=1
Method = query
```

### 获取封面

```
URL = content://providerHost/book/cover/query?path=xxxx
Method = query
```