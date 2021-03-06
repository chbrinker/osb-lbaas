# Load Balancer as a Service
Open Service Broker API (OSB) implementation for managing OpenStack Load-Balanacer-as-a-Service (LBaaS).

## Key Features
* Full stack-based deployment of Load Balancer
* Configuration of Certificates
* Replacement of Certificates

# Usage

## Provisioning

Creating a LBaaS stack.

### Request

````
PUT /service_instances/<instanceId>?accepts_incomplete=false
````

#### Headers

````
Content-Type: application/json
````

#### Body

````
{
	"service_id" : <service_id>,
	"plan_id" : <plan_id>,
	"organization_guid" : <organization_guid>,
	"space_guid" : <space_guid>,
	"context" : <context>,
	"parameters" : <parameters>
}
````

#### Parameters

| Name | Type | Description |
|-------------------|---------------------|-------------------------|
| service_id | String | Service identifier |
| plan_id | String | Plan identifier |
| organization_guid | String | Organization identifier |
| space_guid | String | Space identifier |
| context | Map<String, String> | (optional) |
| parameters | Map<String, String> | (optional) |

### Response

````
202 Accepted

{
    "async": true,
    "dashboard_url": "https://<host>/v2/authentication/<instance_id>"
}
````

---

## Deprovisioning

Delete a LBaaS Stack.

### Request

````
DELETE /v2/service_instances/<instance_id>?service_id=<service_id>&plan_id=<plan_id>
````

### Response

````
200 OK
````

---

## Store Certificates

Create two Secrets, one for a certificate and one for a private key, store their references into a container and save the container's reference in the loadbalancer.

### Request

````
POST /manage/service_instances/<instanceId>/certs
````

#### Headers

````
Content-Type: application/json
````

#### Body

````
{
    "certificate" : <certificate>,
    "private_key" : <private_key>
}
````

#### Parameters

| Name | Type | Description |
|-------------|--------|----------------------------|
| certificate | String | Data of the certificate |
| private_key | String | Private key for decryption |

### Response

````
201 Created
````

### Http Status Codes

| Code | Description |
|-------------|--------|
| 201 | Successful Request |
| 400 | Loadbalancer already has a certificate |
| 410 | Service Broker does not exist |

---

## Update Certificates

Update the certificates and/or private key data for your LBaaS.

### Request

````
PATCH /manage/service_instances/<instanceId>/certs
````

#### Headers

````
Content-Type: application/json
````

#### Body

````
{
    "certificate" : <certificate>,
    "private_key" : <private_key>
}
````

#### Parameters

| Name | Type | Description |
|-------------|--------|----------------------------|
| certificate | String | Data of the certificate |
| private_key | String | Private key for decryption |

### Response

````
201 Created
````

### Http Status Codes

| Code | Description |
|-------------|--------|
| 201 | Successful Request |
| 410 | Service Broker does not exist |

---

## Check if Load Balancer already has a Certificate

### Request

````
GET /manage/service_instances/<instanceId>/certs
````

### Http Status Codes

| Code | Description |
|-------------|--------|
| 200 | Loadbalancer already has a certificate |
| 404 | Loadbalancer doesn't have a certificate yet |

---

## Delete Certificate

Delete the certificate and private key data for your LBaaS.

### Request

````
DELETE /manage/service_instances/<instanceId>/certs
````

### Response

````
204 No Content
````

### Http Status Codes

| Code | Description |
|-------------|--------|
| 204 | Successful Request |
| 410 | Service Broker does not exist |



 
