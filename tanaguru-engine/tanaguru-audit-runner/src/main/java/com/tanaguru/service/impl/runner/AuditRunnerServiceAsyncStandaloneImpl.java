package com.tanaguru.service.impl.runner;

import com.tanaguru.domain.constant.EAuditLogLevel;
import com.tanaguru.domain.constant.EAuditStatus;
import com.tanaguru.domain.entity.audit.Audit;
import com.tanaguru.domain.entity.audit.Page;
import com.tanaguru.repository.*;
import com.tanaguru.runner.AuditRunner;
import com.tanaguru.runner.factory.AuditRunnerFactory;
import com.tanaguru.service.AuditService;
import com.tanaguru.service.ResultAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.transaction.Transactional;

import javax.annotation.PreDestroy;
import java.util.*;

/**
 * @author rcharre
 */

@Service
@Transactional
@ConditionalOnProperty(
        name = "auditrunner.profile",
        havingValue = "async"
)
@EnableScheduling
public class AuditRunnerServiceAsyncStandaloneImpl extends AbstractAuditRunnerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditRunnerServiceAsyncStandaloneImpl.class);

    private final AuditRunnerFactory auditRunnerFactory;
    private final List<Audit> waitingRequests = new ArrayList<>();
    private final Map<AuditRunner, Thread> concurrentAuditRunnerMap = new HashMap<>();

    @Value("${auditrunner.maxConcurrentAudit}")
    private int maxConcurrentAudit;

    @Autowired
    public AuditRunnerServiceAsyncStandaloneImpl(
            PageRepository pageRepository,
            AuditRepository auditRepository,
            AuditRunnerFactory auditRunnerFactory,
            AuditService auditService,
            PageContentRepository pageContentRepository,
            TestResultRepository testResultRepository,
            TestHierarchyResultRepository testHierarchyResultRepository,
            ResultAnalyzerService resultAnalyzerService,
            TestHierarchyRepository testHierarchyRepository,
            ElementResultRepository elementResultRepository) {
        super(pageRepository,
                auditRepository,
                auditService,
                pageContentRepository,
                testResultRepository,
                testHierarchyResultRepository,
                resultAnalyzerService,
                testHierarchyRepository,
                elementResultRepository);
        this.auditRunnerFactory = auditRunnerFactory;
    }

    @Override
    public void runAudit(Audit audit) {
        synchronized (waitingRequests) {
            synchronized (concurrentAuditRunnerMap) {
                waitingRequests.add(audit);
                LOGGER.debug("[Audit {}] Queuing number : {}", audit.getId(), waitingRequests.size());
            }
        }
    }

    /**
     * Scheduled method that launch audits in queue when possible
     */
    @Scheduled(fixedDelay = 10000)
    protected void checkRequests() {
        synchronized (waitingRequests) {
            synchronized (concurrentAuditRunnerMap) {
                while (!waitingRequests.isEmpty() && concurrentAuditRunnerMap.size() < maxConcurrentAudit) {
                    Audit audit = waitingRequests.get(0);
                    waitingRequests.remove(0);
                    auditThread(audit);

                }
            }
        }
    }

    /**
     * Start an audit runner thread
     *
     * @param audit the audit request
     */
    private void auditThread(Audit audit) {
        Optional<AuditRunner> auditRunnerOptional = auditRunnerFactory.create(audit);

        if(auditRunnerOptional.isPresent()){
            AuditRunner auditRunner = auditRunnerOptional.get();
            auditRunner.addListener(this);
            Thread runnerThread = new Thread(auditRunner);
            concurrentAuditRunnerMap.put(auditRunner, runnerThread);
            runnerThread.start();
        }else{
            audit.setStatus(EAuditStatus.ERROR);
            audit = auditRepository.save(audit);
            LOGGER.error("[Audit {}] Unable to start audit", audit.getId());
        }
    }

    @Override
    public void onAuditStartImpl(AuditRunner auditRunner) {
    }

    @Override
    public void onAuditNewPageImpl(AuditRunner auditRunner, Page page) {
    }

    @Override
    public void onAuditEndImpl(AuditRunner auditRunner) {
        synchronized (concurrentAuditRunnerMap) {
            concurrentAuditRunnerMap.remove(auditRunner);
        }
    }

    /**
     * Hooks kill event, cleans audit that will not be launched
     */
    @PreDestroy
    private void cleanRunningAudits() {
        synchronized (waitingRequests) {
            for (Audit audit : waitingRequests) {
                auditService.log(audit, EAuditLogLevel.ERROR, "Audit Interrupted by server");
            }
        }

        synchronized (concurrentAuditRunnerMap) {
            for (AuditRunner runner : concurrentAuditRunnerMap.keySet()) {
                LOGGER.warn("[Audit {}] Interrupting audit", runner.getAudit().getId());
                auditService.log(runner.getAudit(), EAuditLogLevel.ERROR, "Audit Interrupted by server");
                concurrentAuditRunnerMap.get(runner).interrupt();
            }
        }
    }
}
