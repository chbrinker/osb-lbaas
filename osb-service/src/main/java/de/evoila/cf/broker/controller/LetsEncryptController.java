package de.evoila.cf.broker.controller;

import com.google.common.base.Splitter;
import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.exception.ServiceDefinitionDoesNotExistException;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.persistence.mongodb.repository.ServiceInstanceRepository;
import de.evoila.cf.broker.repository.ServiceDefinitionRepository;
import de.evoila.cf.cpi.bosh.LbaaSBoshPlatformService;
import de.evoila.cf.cpi.bosh.LbaaSDeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import io.bosh.client.deployments.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by reneschollmeyer, evoila on 13.03.18.
 */
@RestController
@RequestMapping(value = "/custom/v2/manage/service_instances")
public class LetsEncryptController  {

    private final Logger log = LoggerFactory.getLogger(LetsEncryptController.class);

    private static String INSTANCE_GROUP = "haproxy";

    private LbaaSBoshPlatformService lbaaSBoshPlatformService;

    private ServiceInstanceRepository serviceInstanceRepository;

    private ServiceDefinitionRepository serviceDefinitionRepository;

    private DeploymentManager deploymentManager;

    private BoshProperties boshProperties;

    public LetsEncryptController(LbaaSBoshPlatformService lbaaSBoshPlatformService, ServiceInstanceRepository serviceInstanceRepository, ServiceDefinitionRepository serviceDefinitionRepository,
                                 DeploymentManager deploymentManager, BoshProperties boshProperties) {
        this.lbaaSBoshPlatformService = lbaaSBoshPlatformService;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
        this.deploymentManager = deploymentManager;
        this.boshProperties = boshProperties;
    }

    @PostMapping(value = "/{instanceId}/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity validate(@PathVariable("instanceId") String instanceId,
                                       @RequestBody NsLookupRequest request) throws IOException {

        List<String> domainList = Splitter.on(",").splitToList(request.getDomains().trim());

        NsLookupResponse response = new NsLookupResponse();

        String fip = publicIp(instanceId).getBody().get("publicIp").toString();

        for(String domain : domainList) {
            if(!nslookup(domain, fip)) {
                response.getFalseResults().get("message").add(domain);
            }
        }

        if(response.getFalseResults().get("message").isEmpty()) {
            return new ResponseEntity<>("{ \"message\": \"OK\"}", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(response.getFalseResults(), HttpStatus.ACCEPTED);
        }
    }

    @PostMapping(value = "/{instanceId}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity submit(@PathVariable("instanceId") String instanceId,
                                 @RequestBody NsLookupRequest request) {

        List<String> domainList = Splitter.on(",").splitToList(request.getDomains().trim());

        try {
            updateDeployment(instanceId, request, domainList);
        } catch (ServiceDefinitionDoesNotExistException | PlatformException e) {
            return new ResponseEntity<>("{ \"message\": " + e.getMessage() + " }", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("{ \"message\": \"OK\"}", HttpStatus.OK);
    }

    @GetMapping(value = "/{instanceId}/fip")
    public ResponseEntity<Map> publicIp(@PathVariable("instanceId") String instanceId) throws IOException {
        String publicIp = "";

        Deployment deployment = lbaaSBoshPlatformService.getConnection()
                .connection()
                .deployments()
                .get("sb-" + instanceId).toBlocking().first();

        Manifest manifest = deploymentManager.getMapper().readValue(deployment.getRawManifest(), Manifest.class);

        List<NetworkReference> networks = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getNetworks();

        for(NetworkReference network : networks) {
            if(network.getName().equals(boshProperties.getVipNetwork())) {
                publicIp = network.getStaticIps().get(0);
            }
        }

        if(publicIp.isEmpty()) {
            publicIp = "No public ip specified";
        }

        Map<String, String> response = new HashMap<>();
        response.put("publicIp", publicIp);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private boolean nslookup(String host, String ip) {
        try {
            if(InetAddress.getByName(host.trim()).getHostAddress().equals(ip)) {
                return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }

        return false;
    }

    private void updateDeployment(String instanceId, NsLookupRequest request, List<String> domainList) throws PlatformException,
            ServiceDefinitionDoesNotExistException  {
        Optional<ServiceInstance> instance = serviceInstanceRepository.findById(instanceId);
        Plan plan = serviceDefinitionRepository.getPlan(instance.orElseGet(null).getPlanId());

        Map<String, Object> letsencryptProperties = new HashMap<>();
        letsencryptProperties.put("enabled", true);
        letsencryptProperties.put("email", request.getEmail().trim());
        domainList.stream()
                .map(d -> d.trim())
                .collect(Collectors.toList());
        letsencryptProperties.put("domains", domainList);

        Map<String, Object> letsencrypt = new HashMap<>();
        letsencrypt.put(LbaaSDeploymentManager.LETSENCRYPT, letsencryptProperties);

        lbaaSBoshPlatformService.updateInstance(instance.orElseGet(null), plan, letsencrypt);
    }
}
