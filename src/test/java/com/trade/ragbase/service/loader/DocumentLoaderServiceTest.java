package com.trade.ragbase.service.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.trade.ragbase.service.DocumentLoaderService;

class DocumentLoaderServiceTest {

    private final DocumentLoaderService loaderService = new DocumentLoaderService(List.of(
            new TxtParser(),
            new MarkdownParser(),
            new PdfParser(),
            new DocxParser()));

    @Test
    void loadTxtNormalizesTextAndRemovesBom() {
        byte[] bytes = ("\uFEFF第一行\r\n第二行").getBytes(StandardCharsets.UTF_8);

        ParseResult result = loaderService.load(new ByteArrayInputStream(bytes), "note.txt");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getFullText()).isEqualTo("第一行\n第二行");
    }

    @Test
    void loadTxtFallsBackToGbk() {
        byte[] bytes = "中文内容".getBytes(Charset.forName("GBK"));

        ParseResult result = loaderService.load(new ByteArrayInputStream(bytes), "gbk.txt");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFullText()).isEqualTo("中文内容");
    }

    @Test
    void loadMarkdownStripsSyntaxAndSplitsByHeading() {
        String markdown = """
                # 入职流程

                请阅读 **员工手册**，访问 [门户](https://example.com)。

                ```java
                # not heading
                ```

                ## 年假规则

                - 满一年后可申请年假
                """;

        ParseResult result = loaderService.load(
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)),
                "handbook.md");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPages()).hasSize(2);
        assertThat(result.getPages().get(0).getSectionTitle()).isEqualTo("入职流程");
        assertThat(result.getFullText())
                .contains("员工手册")
                .contains("门户")
                .contains("[代码块]")
                .doesNotContain("**")
                .doesNotContain("https://example.com");
    }

    @Test
    void loadPdfReturnsPageTextAndTitle() throws Exception {
        byte[] pdf = createPdf("Chapter 1 Overview\nPDF body content");

        ParseResult result = loaderService.load(new ByteArrayInputStream(pdf), "policy.pdf");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getTitle()).contains("Chapter 1");
        assertThat(result.getFullText()).contains("PDF");
    }

    @Test
    void loadDocxIncludesParagraphsAndTables() throws Exception {
        byte[] docx = createDocx();

        ParseResult result = loaderService.load(new ByteArrayInputStream(docx), "policy.docx");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPages()).hasSize(1);
        assertThat(result.getFullText())
                .contains("技术规范")
                .contains("[表格]")
                .contains("字段 | 说明");
    }

    @Test
    void unsupportedTypeReturnsFailure() {
        ParseResult result = loaderService.load(InputStreamFactory.empty(), "archive.zip");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("不支持的文件类型");
    }

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                for (String line : text.split("\\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.setAlignment(ParagraphAlignment.LEFT);
            heading.createRun().setText("技术规范");

            document.createParagraph().createRun().setText("接口需要记录审计日志。");

            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("字段");
            table.getRow(0).getCell(1).setText("说明");

            document.write(output);
            return output.toByteArray();
        }
    }

    private static final class InputStreamFactory {
        private static ByteArrayInputStream empty() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
