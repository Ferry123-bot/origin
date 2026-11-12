package com.yunshu.zhixue.modules.question.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.OptionInput;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 题库导入模板与解析。列名以实际表格为准：题干、阶段、题型、难度、选项A~F、正确答案、解析、标签。 */
@Component
public class QuestionWorkbook {

    public static final int MAX_ROWS = 500;
    private static final String[] CHOICE_HEADERS = {
            "题干", "阶段", "题型", "难度", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "正确答案", "解析", "标签"
    };
    private static final String[] SHORT_HEADERS = {"题干", "阶段", "题型", "难度", "正确答案", "解析", "标签"};
    private static final String[] EXPORT_HEADERS = {
            "题干", "阶段", "题型", "难度", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "正确答案", "解析", "标签", "状态"
    };

    private final DataFormatter formatter = new DataFormatter();

    public byte[] template() {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSheet(book, "单选题", CHOICE_HEADERS, new String[]{"单选", "单选题"}, "单选");
            writeSheet(book, "多选题", CHOICE_HEADERS, new String[]{"多选", "多选题"}, "多选");
            writeSheet(book, "简答题", SHORT_HEADERS, new String[]{"简答", "简答题"}, "简答");
            book.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    public byte[] export(List<String[]> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("题目");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(EXPORT_HEADERS[i]);
                sheet.setColumnWidth(i, i == 0 ? 40 * 256 : 16 * 256);
            }
            int rowIndex = 1;
            for (String[] values : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < values.length && i < EXPORT_HEADERS.length; i++) {
                    row.createCell(i).setCellValue(values[i] == null ? "" : values[i]);
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    public List<ParsedQuestion> parse(InputStream input) {
        try (Workbook book = WorkbookFactory.create(input)) {
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            List<ParsedQuestion> rows = new ArrayList<>();
            int recognized = 0;
            for (int i = 0; i < book.getNumberOfSheets(); i++) {
                Sheet sheet = book.getSheetAt(i);
                Header header = readHeader(sheet, evaluator);
                if (header == null) {
                    continue;
                }
                recognized++;
                int last = sheet.getLastRowNum();
                for (int r = 1; r <= last; r++) {
                    Row row = sheet.getRow(r);
                    Map<String, String> cells = readRow(row, header, evaluator);
                    if (isBlank(cells)) {
                        continue;
                    }
                    rows.add(parseRow(sheet.getSheetName(), r + 1, header.choice(), cells));
                    if (rows.size() > MAX_ROWS) {
                        throw new BizException(ErrorCode.IMPORT_INVALID, "单次最多导入 500 道题目");
                    }
                }
            }
            if (recognized == 0) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "表头不匹配，请使用「单选题 / 多选题 / 简答题」模板");
            }
            if (rows.isEmpty()) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "没有可导入的题目");
            }
            return rows;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "无法读取 Excel 文件，请使用模板重新填写");
        }
    }

    private ParsedQuestion parseRow(String sheet, int rowNumber, boolean choice, Map<String, String> cells) {
        List<String> errors = new ArrayList<>();
        String content = required(cells.get("题干"), "题干", 20000, errors);
        String stage = required(cells.get("阶段"), "阶段", 50, errors);
        Integer type = parseType(cells.get("题型"), choice, errors);
        Integer difficulty = parseDifficulty(cells.get("难度"), errors);
        String analysis = optional(cells.get("解析"), "解析", 20000, errors);
        String tags = normalizeTags(cells.get("标签"), errors);
        String answerText = cells.getOrDefault("正确答案", "");
        List<OptionInput> options = List.of();
        String answer = null;
        if (choice) {
            options = parseOptions(cells, type, answerText, errors);
        } else if (type != null && type == 4) {
            answer = required(answerText, "正确答案", 20000, errors);
        }
        return new ParsedQuestion(sheet, rowNumber, content, stage, type, difficulty, analysis, tags, answer, options,
                errors.isEmpty() ? "ok" : "error", errors);
    }

    private List<OptionInput> parseOptions(Map<String, String> cells, Integer type, String answerText, List<String> errors) {
        String[] keys = {"A", "B", "C", "D", "E", "F"};
        List<OptionInput> options = new ArrayList<>();
        boolean gap = false;
        boolean gapReported = false;
        for (String key : keys) {
            String text = cells.getOrDefault("选项" + key, "").trim();
            if (text.isEmpty()) {
                gap = true;
                continue;
            }
            if (gap && !gapReported) {
                errors.add("选项须从 A 连续填写，不能跳过空项");
                gapReported = true;
            }
            if (text.length() > 500) {
                errors.add("选项" + key + "不能超过 500 字");
            }
            options.add(new OptionInput(key, text, false));
        }
        if (options.size() < 2) {
            errors.add("选择题至少填写两个选项");
        }
        List<String> correct = parseAnswerKeys(answerText, type, errors);
        if (correct == null) {
            return options;
        }
        List<OptionInput> marked = new ArrayList<>();
        for (OptionInput option : options) {
            marked.add(new OptionInput(option.key(), option.text(), correct.contains(option.key())));
        }
        long hit = marked.stream().filter(item -> Boolean.TRUE.equals(item.correct())).count();
        if (hit != correct.size()) {
            errors.add("正确答案中的选项在表格里不存在");
        } else if (hit == 0) {
            errors.add("请至少指定一个正确答案");
        } else if (type != null && type == 1 && hit != 1) {
            errors.add("单选题只能有一个正确答案");
        }
        return marked;
    }

    private List<String> parseAnswerKeys(String text, Integer type, List<String> errors) {
        if (text == null || text.isBlank()) {
            errors.add("请填写正确答案");
            return null;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT)
                .replace('，', ',')
                .replace('、', ',')
                .replace('；', ',')
                .replace(';', ',')
                .replace(" ", "");
        List<String> keys = new ArrayList<>();
        if (normalized.contains(",")) {
            for (String part : normalized.split(",")) {
                if (!part.isEmpty()) {
                    keys.add(part);
                }
            }
        } else if (normalized.matches("[A-F]+")) {
            for (char letter : normalized.toCharArray()) {
                keys.add(String.valueOf(letter));
            }
        } else {
            errors.add("正确答案请填写选项字母，如 A 或 A,B,C");
            return null;
        }
        if (keys.stream().distinct().count() != keys.size() || keys.stream().anyMatch(key -> !key.matches("[A-F]"))) {
            errors.add("正确答案请填写不重复的选项字母");
            return null;
        }
        if (type != null && type == 1 && keys.size() != 1) {
            errors.add("单选题只能有一个正确答案");
        }
        if (type != null && type == 2 && keys.isEmpty()) {
            errors.add("多选题至少有一个正确答案");
        }
        return keys;
    }

    private Integer parseType(String text, boolean choice, List<String> errors) {
        String value = text == null ? "" : text.trim();
        Integer type = switch (value) {
            case "单选", "单选题" -> 1;
            case "多选", "多选题" -> 2;
            case "简答", "简答题" -> 4;
            default -> null;
        };
        if (type == null) {
            errors.add(choice ? "题型请填「单选」或「多选」" : "题型请填「简答」");
            return null;
        }
        if (choice && type == 4) {
            errors.add("简答题请放到「简答题」工作表，不要填写选项");
            return null;
        }
        if (!choice && type != 4) {
            errors.add("选择题请使用带选项列的工作表");
            return null;
        }
        return type;
    }

    private Integer parseDifficulty(String text, List<String> errors) {
        String value = text == null ? "" : text.trim();
        return switch (value) {
            case "简单" -> 1;
            case "中等" -> 2;
            case "困难" -> 3;
            default -> {
                errors.add("难度请填「简单」「中等」或「困难」");
                yield null;
            }
        };
    }

    private String normalizeTags(String text, List<String> errors) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace('，', ',').replace('、', ',');
        if (normalized.length() > 255) {
            errors.add("标签不能超过 255 字");
        }
        return normalized;
    }

    private static String required(String text, String label, int max, List<String> errors) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            errors.add("请填写" + label);
            return "";
        }
        if (value.length() > max) {
            errors.add(label + "不能超过 " + max + " 字");
        }
        return value;
    }

    private static String optional(String text, String label, int max, List<String> errors) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        if (value.length() > max) {
            errors.add(label + "不能超过 " + max + " 字");
        }
        return value;
    }

    private Header readHeader(Sheet sheet, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(0);
        if (row == null) {
            return null;
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        short last = row.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String name = normalizeHeader(cellText(row.getCell(i), evaluator));
            if (!name.isEmpty()) {
                columns.put(name, i);
            }
        }
        if (!columns.containsKey("题干") || !columns.containsKey("阶段") || !columns.containsKey("题型")
                || !columns.containsKey("难度") || !columns.containsKey("正确答案")) {
            return null;
        }
        boolean choice = columns.containsKey("选项A");
        if (choice && !columns.containsKey("选项B")) {
            return null;
        }
        return new Header(choice, columns);
    }

    private Map<String, String> readRow(Row row, Header header, FormulaEvaluator evaluator) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : header.columns().entrySet()) {
            cells.put(entry.getKey(), row == null ? "" : cellText(row.getCell(entry.getValue()), evaluator));
        }
        return cells;
    }

    private static boolean isBlank(Map<String, String> cells) {
        return cells.values().stream().allMatch(String::isEmpty);
    }

    private static String normalizeHeader(String text) {
        String value = text.replace(" ", "").replace("\u00A0", "");
        if ("题目".equals(value)) {
            return "题干";
        }
        if ("类型".equals(value)) {
            return "题型";
        }
        return value;
    }

    private String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void writeSheet(XSSFWorkbook book, String name, String[] headers, String[] typeOptions, String typeSample) {
        Sheet sheet = book.createSheet(name);
        CellStyle head = book.createCellStyle();
        Font font = book.createFont();
        font.setBold(true);
        head.setFont(font);
        head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row row = sheet.createRow(0);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        for (int i = 0; i < headers.length; i++) {
            var cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(head);
            sheet.setColumnWidth(i, "题干".equals(headers[i]) ? 48 * 256 : 16 * 256);
            comment(drawing, cell, i, hint(headers[i], typeSample));
        }
        addDropdown(sheet, columnOf(headers, "题型"), typeOptions);
        addDropdown(sheet, columnOf(headers, "难度"), new String[]{"简单", "中等", "困难"});
    }

    private static int columnOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private static String hint(String header, String typeSample) {
        return switch (header) {
            case "题干" -> "必填。题目正文，可含代码。";
            case "阶段" -> "必填。须与系统中已有阶段名称完全一致。";
            case "题型" -> "必填。本表填写「" + typeSample + "」。";
            case "难度" -> "必填。简单、中等或困难。";
            case "正确答案" -> "单选填一个字母；多选填 A,B,C；简答填答案正文。";
            case "解析" -> "选填。";
            case "标签" -> "选填。多个标签用逗号分隔。";
            default -> "选填。从 A 起连续填写，可留空。";
        };
    }

    private static void comment(Drawing<?> drawing, org.apache.poi.ss.usermodel.Cell cell, int column, String text) {
        Comment comment = drawing.createCellComment(new XSSFClientAnchor(0, 0, 0, 0, column, 1, column + 2, 4));
        comment.setString(new XSSFRichTextString(text));
        cell.setCellComment(comment);
    }

    private static void addDropdown(Sheet sheet, int column, String[] options) {
        var helper = sheet.getDataValidationHelper();
        var constraint = helper.createExplicitListConstraint(options);
        var address = new CellRangeAddressList(1, MAX_ROWS, column, column);
        var validation = helper.createValidation(constraint, address);
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    public record ParsedQuestion(String sheet, int row, String content, String stage, Integer type, Integer difficulty,
                                 String analysis, String tags, String answer, List<OptionInput> options,
                                 String status, List<String> messages) {
    }

    private record Header(boolean choice, Map<String, Integer> columns) {
    }
}
