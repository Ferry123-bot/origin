package com.yunshu.zhixue.modules.course.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CourseWorkbook {

    public static final int MAX_ROWS = 500;
    public static final int MAX_MINUTES = 600_000;
    private static final String OTHER = "其他学科";
    private static final String[] HEADERS = {"课程名称", "所属学科", "来源", "时长（分钟）", "BV号", "封面", "播放链接"};
    private static final String[] COMMENTS = {
            "必填。课程标题，最长 100 字。",
            "必填。多个学科用中文顿号「、」或英文逗号「,」分隔，每个名称必须与课程库里已有学科完全一致。不属于已有学科时填「其他学科」。",
            "必填。只能填「B站」或「其他」，填其他值该行不通过。",
            "必填。单位是分钟，可填小数（如 12.5），不能小于等于 0。小数会四舍五入为整数分钟。",
            "来源填「B站」时必填，形如 BV1xx411c7mD。播放链接留空时按它自动生成。",
            "选填。课程封面图的完整 URL（http/https 开头），后台不上传图片，直接填地址。",
            "选填。留空时：来源为 B 站则按 BV 号生成 B 站地址，来源为其他则存空。"
    };

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("课程导入");
            Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(style);
                comment(helper, drawing, cell, COMMENTS[i]);
            }
            int[] widths = {48, 36, 12, 16, 18, 42, 42};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(new String[]{"B站", "其他"});
            DataValidation validation = validationHelper.createValidation(constraint, new CellRangeAddressList(1, MAX_ROWS, 2, 2));
            validation.setShowErrorBox(true);
            validation.createErrorBox("来源不正确", "只能填「B站」或「其他」");
            sheet.addValidationData(validation);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成导入模板失败");
        }
    }

    public List<ParsedCourse> read(InputStream input, Map<String, Long> subjectNames, Set<String> existingBvids, Set<String> existingNames) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "Excel 中没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (!headersMatch(header, workbook.getCreationHelper().createFormulaEvaluator())) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "表头须为：课程名称、所属学科、来源、时长（分钟）、BV号、封面、播放链接");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<ParsedCourse> rows = new ArrayList<>();
            Set<String> seenBvids = new HashSet<>();
            Set<String> seenNames = new HashSet<>();
            int dataRows = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String[] cells = readCells(row, evaluator);
                if (isBlank(cells)) {
                    continue;
                }
                dataRows++;
                if (dataRows > MAX_ROWS) {
                    throw new BizException(ErrorCode.IMPORT_INVALID, "单次最多导入 500 条");
                }
                rows.add(parseRow(i + 1, cells, subjectNames, existingBvids, existingNames, seenBvids, seenNames));
            }
            return rows;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "无法读取 Excel 文件，请使用模板重新填写");
        }
    }

    public static String formatDuration(int minutes) {
        return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
    }

    private ParsedCourse parseRow(int rowNumber, String[] cells, Map<String, Long> subjectNames, Set<String> existingBvids,
                                  Set<String> existingNames, Set<String> seenBvids, Set<String> seenNames) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String name = cells[0];
        if (name.isEmpty()) {
            errors.add("请填写课程名称");
        } else if (name.length() > 100) {
            errors.add("课程名称不超过 100 字");
        }

        SubjectBinding subjects = bindSubjects(cells[1], subjectNames, errors, warnings);
        Integer source = parseSource(cells[2], errors);
        MinuteValue minutes = parseMinutes(cells[3]);
        if (minutes.error() != null) {
            errors.add(minutes.error());
        } else if (minutes.rounded()) {
            warnings.add("时长已四舍五入为 " + minutes.minutes() + " 分钟");
        }

        String bvid = cells[4].isEmpty() ? null : cells[4];
        if (bvid != null && !bvid.matches("^BV[0-9A-Za-z]{10}$")) {
            errors.add("BV号格式不正确，形如 BV1xx411c7mD");
            bvid = null;
        }
        if (source != null && source == 1 && cells[4].isEmpty()) {
            errors.add("来源为 B站时须填写 BV号");
        }

        String cover = optionalUrl(cells[5], "封面", errors);
        String link = optionalUrl(cells[6], "播放链接", errors);
        if (cells[6].isEmpty() && source != null && source == 1 && bvid != null) {
            link = "https://www.bilibili.com/video/" + bvid;
        }

        if (errors.isEmpty() && bvid != null) {
            String key = bvid.toLowerCase(Locale.ROOT);
            if (existingBvids.contains(key)) {
                errors.add("BV号已存在");
            } else if (!seenBvids.add(key)) {
                errors.add("文件中 BV号重复");
            }
        }
        if (errors.isEmpty() && !name.isEmpty()) {
            if (existingNames.contains(name)) {
                warnings.add("已存在同名课程");
            } else if (!seenNames.add(name)) {
                warnings.add("文件中已有同名课程");
            }
        }

        String status = !errors.isEmpty() ? "error" : (!warnings.isEmpty() ? "warning" : "ok");
        List<String> messages = new ArrayList<>(errors);
        messages.addAll(warnings);
        Integer duration = minutes.error() == null ? minutes.minutes() : null;
        List<Long> extras = subjects.extraIds().isEmpty() ? List.of() : List.copyOf(subjects.extraIds());
        return new ParsedCourse(rowNumber, name, subjects.text(), source, sourceLabel(source, cells[2]), duration,
                cells[4], bvid, cover, blankToNull(link), subjects.subjectId(), extras, status, messages);
    }

    private SubjectBinding bindSubjects(String text, Map<String, Long> subjectNames, List<String> errors, List<String> warnings) {
        if (text.isEmpty()) {
            errors.add("请填写所属学科");
            return SubjectBinding.empty(text);
        }
        String[] parts = text.split("[、,，]");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = clean(part);
            if (!token.isEmpty() && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            errors.add("请填写所属学科");
            return SubjectBinding.empty(text);
        }
        boolean other = false;
        List<Long> ids = new ArrayList<>();
        for (String token : tokens) {
            if (isOther(token)) {
                other = true;
                if (!OTHER.equals(token)) {
                    warnings.add("「" + token + "」按其他学科导入");
                }
                continue;
            }
            Long id = subjectNames.get(token);
            if (id == null) {
                errors.add("未找到学科「" + token + "」");
            } else if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (other && !ids.isEmpty()) {
            errors.add("「其他学科」不能与具体学科同时填写");
        }
        if (other) {
            return new SubjectBinding(text, null, List.of());
        }
        if (ids.isEmpty()) {
            return new SubjectBinding(text, null, List.of());
        }
        return new SubjectBinding(text, ids.get(0), ids.subList(1, ids.size()));
    }

    private static boolean isOther(String token) {
        return OTHER.equals(token) || token.startsWith("其他学科（") || token.startsWith("其他学科(");
    }

    private static Integer parseSource(String text, List<String> errors) {
        if (text.isEmpty()) {
            errors.add("请填写来源");
            return null;
        }
        if ("B站".equals(text)) {
            return 1;
        }
        if ("其他".equals(text)) {
            return 2;
        }
        errors.add("来源只能填「B站」或「其他」");
        return null;
    }

    private static MinuteValue parseMinutes(String text) {
        if (text.isEmpty()) {
            return MinuteValue.error("请填写时长（分钟）");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return MinuteValue.error("时长须为大于 0 的分钟数");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return MinuteValue.error("时长须大于 0");
        }
        boolean fractional = value.stripTrailingZeros().scale() > 0;
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ZERO) <= 0) {
            return MinuteValue.error("时长须大于 0");
        }
        if (rounded.compareTo(BigDecimal.valueOf(MAX_MINUTES)) > 0) {
            return MinuteValue.error("时长过长");
        }
        return new MinuteValue(rounded.intValue(), fractional, null);
    }

    private static String optionalUrl(String text, String label, List<String> errors) {
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > 500 || !text.matches("(?i)^https?://\\S+$")) {
            errors.add(label + "须为 http 或 https 地址");
            return null;
        }
        return text;
    }

    private static String sourceLabel(Integer source, String raw) {
        if (source != null && source == 1) {
            return "B站";
        }
        if (source != null && source == 2) {
            return "其他";
        }
        return raw;
    }

    private boolean headersMatch(Row header, FormulaEvaluator evaluator) {
        if (header == null) {
            return false;
        }
        String[] cells = readCells(header, evaluator);
        if (!"课程名称".equals(cells[0]) || !"所属学科".equals(cells[1]) || !"来源".equals(cells[2])) {
            return false;
        }
        if (!"时长（分钟）".equals(cells[3]) && !"时长(分钟)".equals(cells[3])) {
            return false;
        }
        return "BV号".equals(cells[4]) && "封面".equals(cells[5]) && "播放链接".equals(cells[6]);
    }

    private static String[] readCells(Row row, FormulaEvaluator evaluator) {
        String[] cells = new String[HEADERS.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = row == null ? "" : cellText(row.getCell(i), evaluator);
        }
        return cells;
    }

    private static boolean isBlank(String[] cells) {
        for (String cell : cells) {
            if (!cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluator.evaluateFormulaCell(cell);
        }
        String text = switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
        return clean(text);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void comment(CreationHelper helper, Drawing<?> drawing, Cell cell, String text) {
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 4);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        comment.setAuthor("课程库");
        cell.setCellComment(comment);
    }

    public record ParsedCourse(
            int row,
            String name,
            String subjects,
            Integer source,
            String sourceLabel,
            Integer durationMinutes,
            String bvidText,
            String bvid,
            String coverUrl,
            String link,
            Long subjectId,
            List<Long> subjectIds,
            String status,
            List<String> messages
    ) {
    }

    private record SubjectBinding(String text, Long subjectId, List<Long> extraIds) {
        static SubjectBinding empty(String text) {
            return new SubjectBinding(text, null, List.of());
        }
    }

    private record MinuteValue(Integer minutes, boolean rounded, String error) {
        static MinuteValue error(String error) {
            return new MinuteValue(null, false, error);
        }
    }
}
