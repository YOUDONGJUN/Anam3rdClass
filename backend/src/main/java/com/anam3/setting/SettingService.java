package com.anam3.setting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anam3.common.AppProperties;

@Service
@Transactional
public class SettingService {

    static final String INVITE_CODE = "invite_code";
    static final String LATEST_COHORT = "latest_cohort";

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
        save(INVITE_CODE, code.strip());
    }

    /** 가입 때 고를 수 있는 가장 최근 기수 (새 기수가 들어오면 운영 페이지에서 올린다) */
    @Transactional(readOnly = true)
    public int latestCohort() {
        return settings.findById(LATEST_COHORT)
                .map(s -> Integer.parseInt(s.getValue()))
                .orElse(props.latestCohort() == null ? 62 : props.latestCohort());
    }

    public void changeLatestCohort(int cohort) {
        save(LATEST_COHORT, String.valueOf(cohort));
    }

    private void save(String key, String value) {
        settings.findById(key).ifPresentOrElse(
                s -> s.changeValue(value),
                () -> settings.save(new AppSetting(key, value)));
    }
}
