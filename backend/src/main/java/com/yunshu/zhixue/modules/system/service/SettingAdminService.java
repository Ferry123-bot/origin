package com.yunshu.zhixue.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.SettingsSaveRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.SettingsView;
import com.yunshu.zhixue.modules.system.entity.SystemSetting;
import com.yunshu.zhixue.modules.system.mapper.SystemSettingMapper;
import com.yunshu.zhixue.support.ChangeSummary;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class SettingAdminService {

    static final String SITE_NAME = "site_name";
    static final String SITE_DESCRIPTION = "site_description";
    static final String LOGO_URL = "logo_url";
    static final String DEFAULT_AVATAR = "default_avatar";

    private static final Map<String, String> DEFAULTS = Map.of(
            SITE_NAME, "云枢智学",
            SITE_DESCRIPTION, "",
            LOGO_URL, "",
            DEFAULT_AVATAR, "");

    private final SystemSettingMapper systemSettingMapper;
    private final OperationLogWriter operationLogWriter;

    public SettingAdminService(SystemSettingMapper systemSettingMapper, OperationLogWriter operationLogWriter) {
        this.systemSettingMapper = systemSettingMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public SettingsView get() {
        return new SettingsView(value(SITE_NAME), value(SITE_DESCRIPTION), value(LOGO_URL), value(DEFAULT_AVATAR));
    }

    @Transactional
    public void save(SettingsSaveRequest request) {
        ChangeSummary summary = new ChangeSummary()
                .change("平台名称", value(SITE_NAME), request.siteName().trim())
                .change("平台描述", value(SITE_DESCRIPTION), blank(request.siteDescription()))
                .change("Logo", value(LOGO_URL), blank(request.logoUrl()))
                .change("默认头像", value(DEFAULT_AVATAR), blank(request.defaultAvatar()));
        upsert(SITE_NAME, request.siteName().trim(), "平台名称");
        upsert(SITE_DESCRIPTION, blank(request.siteDescription()), "平台描述");
        upsert(LOGO_URL, blank(request.logoUrl()), "平台 Logo");
        upsert(DEFAULT_AVATAR, blank(request.defaultAvatar()), "默认头像");
        operationLogWriter.write("修改", "系统设置", summary.finish("系统设置"));
    }

    @Transactional
    public void reset() {
        DEFAULTS.forEach((key, value) -> upsert(key, value, remark(key)));
        operationLogWriter.write("修改", "系统设置", "系统设置：恢复默认值，平台名称、平台描述、Logo、默认头像都改回初始内容");
    }

    private void upsert(String key, String value, String remark) {
        SystemSetting row = systemSettingMapper.selectOne(Wrappers.<SystemSetting>lambdaQuery().eq(SystemSetting::getConfigKey, key));
        if (row == null) {
            row = new SystemSetting();
            row.setConfigKey(key);
            row.setConfigValue(value);
            row.setRemark(remark);
            systemSettingMapper.insert(row);
            return;
        }
        row.setConfigValue(value);
        systemSettingMapper.updateById(row);
    }

    private String value(String key) {
        SystemSetting row = systemSettingMapper.selectOne(Wrappers.<SystemSetting>lambdaQuery().eq(SystemSetting::getConfigKey, key));
        if (row == null || row.getConfigValue() == null) {
            return DEFAULTS.getOrDefault(key, "");
        }
        return row.getConfigValue();
    }

    private String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String remark(String key) {
        return switch (key) {
            case SITE_NAME -> "平台名称";
            case SITE_DESCRIPTION -> "平台描述";
            case LOGO_URL -> "平台 Logo";
            default -> "默认头像";
        };
    }
}
