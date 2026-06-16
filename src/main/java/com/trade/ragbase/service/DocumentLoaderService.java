package com.trade.ragbase.service;

import com.trade.ragbase.service.loader.DocumentParser;
import com.trade.ragbase.service.loader.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentLoaderService {

    private final Map<String, DocumentParser> parsers;

    public DocumentLoaderService(List<DocumentParser> parserList) {
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(
                        parser -> parser.supportedType().toUpperCase(Locale.ROOT),
                        Function.identity()));
        log.info("已加载文档解析器：{}", parsers.keySet());
    }

    public ParseResult load(InputStream inputStream, String fileName) {
        String fileType = detectFileType(fileName);
        DocumentParser parser = parsers.get(fileType);

        if (parser == null) {
            log.warn("[文档加载] 不支持的文件类型：{}，文件：{}", fileType, fileName);
            return ParseResult.failure("不支持的文件类型：" + fileType + "，目前支持：PDF / DOCX / MD / TXT");
        }

        log.info("[文档加载] 开始解析：fileName={}，type={}", fileName, fileType);
        long start = System.currentTimeMillis();
        ParseResult result = parser.parse(inputStream, fileName);
        long elapsed = System.currentTimeMillis() - start;

        if (result.isSuccess()) {
            log.info("[文档加载] 解析完成：fileName={}，页数={}，耗时={}ms",
                    fileName, result.getTotalPages(), elapsed);
        } else {
            log.warn("[文档加载] 解析失败：fileName={}，原因={}", fileName, result.getErrorMsg());
        }

        return result;
    }

    private String detectFileType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "UNKNOWN";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "UNKNOWN";
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> "PDF";
            case "docx" -> "DOCX";
            case "md", "markdown" -> "MD";
            case "txt" -> "TXT";
            default -> ext.toUpperCase(Locale.ROOT);
        };
    }
}
