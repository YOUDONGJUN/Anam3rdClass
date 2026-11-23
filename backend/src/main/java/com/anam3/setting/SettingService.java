package com.anam3.setting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.AppProperties;

@Service
@Transactional
public class SettingService {

    static final String INVITE_CODE = "invite_code";

    private final AppSettingRepository settings;
    private final AppProperties props;

    public SettingService(AppSettingRepository settings, AppProperties props) {
        this.settings = settings;
        this.props = props;
    }

    /** 운영자가 화면에서 바꾼 값이 있으면 그 값, 없으면 INVITE_CODE 환경변수 값 */
    @Transactional(readOnly = true)
    public String inviteCode() {
        return settings.findById(INVITE_CODE)
                .map(AppSetting::getValue)
                .orElse(props.inviteCode() == null ? "" : props.inviteCode());
    }

    public void changeInviteCode(String code) {
        String value = code.strip();
        settings.findById(INVITE_CODE).ifPresentOrElse(
                s -> s.changeValue(value),
                () -> settings.save(new AppSetting(INVITE_CODE, value)));
    }
}
