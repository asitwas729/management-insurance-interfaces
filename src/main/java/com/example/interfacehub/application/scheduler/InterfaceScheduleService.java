package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.scheduler.InterfaceSchedule;
import com.example.interfacehub.infrastructure.persistence.InterfaceScheduleRepository;
import com.example.interfacehub.presentation.CreateInterfaceScheduleRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterfaceScheduleService {

    private final InterfaceScheduleRepository scheduleRepository;
    private final InterfaceJobScheduler jobScheduler;

    public InterfaceScheduleService(
        InterfaceScheduleRepository scheduleRepository,
        InterfaceJobScheduler jobScheduler
    ) {
        this.scheduleRepository = scheduleRepository;
        this.jobScheduler = jobScheduler;
    }

    @Transactional(readOnly = true)
    public List<InterfaceSchedule> findAll() {
        return scheduleRepository.findAll();
    }

    @Transactional
    public InterfaceSchedule create(CreateInterfaceScheduleRequest request) {
        if (scheduleRepository.findByInterfaceCode(request.interfaceCode()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Schedule already exists for interface: " + request.interfaceCode());
        }
        InterfaceSchedule schedule = InterfaceSchedule.create(
            request.interfaceCode(), request.cronExpression(), request.payloadTemplate()
        );
        InterfaceSchedule saved = scheduleRepository.save(schedule);
        jobScheduler.register(saved);
        return saved;
    }

    @Transactional
    public InterfaceSchedule updateEnabled(String interfaceCode, boolean enabled) {
        InterfaceSchedule schedule = scheduleRepository.findByInterfaceCode(interfaceCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Schedule not found for: " + interfaceCode));
        schedule.setEnabled(enabled);
        if (enabled) {
            jobScheduler.register(schedule);
        } else {
            jobScheduler.cancel(interfaceCode);
        }
        return schedule;
    }

    @Transactional
    public void delete(String interfaceCode) {
        InterfaceSchedule schedule = scheduleRepository.findByInterfaceCode(interfaceCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Schedule not found for: " + interfaceCode));
        jobScheduler.cancel(interfaceCode);
        scheduleRepository.delete(schedule);
    }
}
