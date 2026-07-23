# 📋 Form Configuration Service API Documentation

Welcome to the **Form Configuration Service** API documentation. This document describes the available REST endpoints, required headers, request payloads, and response formats for both **V1** and **V2** APIs.

---

## 🚀 Server Configurations
- **Base URL**: `http://localhost:7010`

---

## 🛠️ Swagger UI & OpenAPI Specification
This project integrates **Springdoc OpenAPI (Swagger)** for dynamic API documentation.
- **Interactive Swagger UI**: `http://localhost:7010/swagger-ui.html`
- **Raw OpenAPI Spec**: [docs/openapi.json](openapi.json)

---

## 📡 V1 API Reference

### 1. Create Form Configuration (V1)
Creates a new form configuration mapping in the system.
* **Method**: `POST`
* **Path**: `/formsConfig/create`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
  * `Content-Type`: `application/json`
* **Request Body**:
```json
{
  "request": {
    "name": "Profile Form Configuration",
    "type": "profile",
    "subtype": "personal-details",
    "portal": "web",
    "clientVersion": 1.0,
    "criteria": {
      "rootOrg": "karmayogi",
      "role": "PUBLIC"
    },
    "data": {
      "fields": [
        {
          "name": "firstName",
          "type": "text",
          "required": true
        }
      ]
    }
  }
}
```

---

### 2. Read Form Configuration (V1)
Retrieves the form configuration matching the requested parameters.
* **Method**: `POST`
* **Path**: `/formsConfig/read`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
  * `Content-Type`: `application/json`
* **Request Body**:
```json
{
  "request": {
    "type": "profile",
    "subtype": "personal-details",
    "portal": "web",
    "clientVersion": 1.0
  }
}
```

---

### 3. Update Form Configuration (V1)
Updates an existing form configuration queried by criteria, or creates one if it doesn't exist.
* **Method**: `PUT`
* **Path**: `/formsConfig/update`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
  * `Content-Type`: `application/json`
* **Request Body**:
```json
{
  "request": {
    "name": "Profile Form Configuration Updated",
    "type": "profile",
    "subtype": "personal-details",
    "portal": "web",
    "clientVersion": 1.0,
    "criteria": {
      "rootOrg": "karmayogi",
      "role": "PUBLIC"
    },
    "data": {
      "fields": [
        {
          "name": "firstName",
          "type": "text",
          "required": true
        }
      ]
    }
  }
}
```

---

### 4. Read Form Configuration For Admin (V1)
Retrieves the form configuration based on admin credentials.
* **Method**: `POST`
* **Path**: `/formsConfig/admin/read/{userId}`
* **Headers**:
  * `x-authenticated-user-orgid`: (Required) Admin organization ID.
  * `x-authenticated-user-roles`: (Required) Admin user roles (comma-separated).
  * `Content-Type`: `application/json`
* **Path Variables**:
  * `userId`: The ID of the administrator.
* **Request Body**:
```json
{
  "request": {
    "type": "profile",
    "subtype": "personal-details",
    "portal": "web",
    "clientVersion": 1.0,
    "criteria": {
      "rootOrg": "karmayogi",
      "role": "PUBLIC"
    }
  }
}
```

---

## 📡 V2 API Reference

### 1. Create Form Configuration (V2)
Creates a new configuration mapping accepting **only** non-nullable columns.
* **Method**: `POST`
* **Path**: `/formsConfig/v2/create`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
  * `Content-Type`: `application/json`
* **Request Body**:
```json
{
  "request": {
    "name": "V2 Quick Profile Configuration",
    "type": "v2-profile",
    "subtype": "basic",
    "portal": "mobile",
    "clientVersion": 2.0
  }
}
```

---

### 2. Read Form Configuration By ID (V2)
Retrieves a form configuration record directly using its primary key database ID.
* **Method**: `GET`
* **Path**: `/formsConfig/v2/admin/read/{formId}`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
* **Path Variables**:
  * `formId`: (Required) The primary key ID of the form configuration record.

---

### 3. Update Form Configuration (V2)
Performs a partial update of a configuration record queried by `id` or `formId`. Only the supplied fields in the request payload are updated.
* **Method**: `PUT`
* **Path**: `/formsConfig/v2/update`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
  * `Content-Type`: `application/json`
* **Request Body**:
```json
{
  "request": {
    "id": 1,
    "name": "V2 Profile Configuration (Updated)",
    "type": "v2-profile",
    "subtype": "basic",
    "portal": "mobile",
    "clientVersion": 2.0
  }
}
```

---

### 4. List Form Configurations (V2)
Lists all form configuration mappings present in the database, returning only their non-nullable attributes.
* **Method**: `GET`
* **Path**: `/formsConfig/v2/admin/list`
* **Headers**:
  * `x-authenticated-user-token`: (Required) User authentication token.
* **Response Payload Example**:
```json
{
  "id": "api.form.list.v2",
  "ver": "v1",
  "ts": "2026-07-23 09:41:03.000",
  "message": null,
  "params": {
    "resmsgid": "673f8a00-50d4-4bb0-8025-0ad3cf2bc506",
    "status": "successful",
    "err": null,
    "errmsg": null
  },
  "responseCode": "OK",
  "result": {
    "formConfigurations": [
      {
        "id": 1,
        "name": "V2 Quick Profile Configuration",
        "type": "v2-profile",
        "subtype": "basic",
        "portal": "mobile",
        "clientVersion": 2.0
      }
    ]
  }
}
```
