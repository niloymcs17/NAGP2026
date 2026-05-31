# API Endpoint Documentation

This document provides a comprehensive catalog of the REST APIs exposed by the **Leave Portal**, routing through the API Gateway at `http://localhost:8080`.

> **Note on Error Responses**: All error responses across every service are returned as a structured JSON `ErrorResponse` object (not plain text), with the following shape:
> ```json
> {
>     "status": 400,
>     "error": "Bad Request",
>     "message": "Human-readable error detail",
>     "path": "/leaves/apply"
> }
> ```
> The HTTP status codes and example `message` values are documented per endpoint below.

---

## 1. Authentication Service

### Login (Get JWT Token)
Authenticate credentials and obtain a JWT Bearer Token.
* **HTTP Method**: `POST`
* **Path**: `/auth/login`
* **Request Header**: `Content-Type: application/json`
* **Request Body**:
  ```json
  {
      "username": "employee1",
      "password": "password"
  }
  ```
* **Response (200 OK)**:
  ```json
  {
      "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiRU1QTE9ZRUUiLCJ1c2VybmFtZSI6ImVtcGxveWVlMSIsInN1YiI6IjEifQ...",
      "userId": 1,
      "username": "employee1",
      "role": "EMPLOYEE"
  }
  ```
* **Response (401 Unauthorized)**:
  ```json
  {
      "status": 401,
      "error": "Unauthorized",
      "message": "Invalid username or password",
      "path": "/auth/login"
  }
  ```

---

## 2. Leave Management Service
*All endpoints below require the `Authorization: Bearer <TOKEN>` header.*

### Get Leave Balances
Retrieve the logged-in user's leave balances.
* **HTTP Method**: `GET`
* **Path**: `/leaves/balances`
* **Response (200 OK)**:
  ```json
  [
      {
          "id": 1,
          "employeeId": 1,
          "leaveType": "CASUAL",
          "allocated": 12,
          "used": 0,
          "remaining": 12
      },
      {
          "id": 2,
          "employeeId": 1,
          "leaveType": "SICK",
          "allocated": 10,
          "used": 0,
          "remaining": 10
      },
      {
          "id": 3,
          "employeeId": 1,
          "leaveType": "PRIVILEGE",
          "allocated": 15,
          "used": 0,
          "remaining": 15
      }
  ]
  ```
  > **Note**: `remaining` is a derived field (`allocated - used`) computed at runtime; it is not stored in the database.

* **Response (404 Not Found — no leave balance record exists)**:
  ```json
  {
      "status": 404,
      "error": "Not Found",
      "message": "No leave balance found for employee ID: 1",
      "path": "/leaves/balances"
  }
  ```

### Apply for Leave (Employee)
Submit a leave request.
* **HTTP Method**: `POST`
* **Path**: `/leaves/apply`
* **Request Body**:
  ```json
  {
      "leaveType": "CASUAL",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03",
      "reason": "Family vacation",
      "managerId": 3
  }
  ```
  > **Note**: The `numberOfDays` field is **always auto-calculated** by the server from the `startDate`/`endDate` range, excluding weekends and public holidays. Any client-supplied value for `numberOfDays` is ignored and overwritten.

* **Response (201 Created)**:
  ```json
  {
      "id": 1,
      "employeeId": 1,
      "leaveType": "CASUAL",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03",
      "numberOfDays": 3,
      "reason": "Family vacation",
      "managerId": 3,
      "status": "PENDING",
      "rejectionReason": null
  }
  ```
* **Response (400 Bad Request — start date in the past)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Start date cannot be in the past",
      "path": "/leaves/apply"
  }
  ```
* **Response (400 Bad Request — invalid date range)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Start date must be less than or equal to end date",
      "path": "/leaves/apply"
  }
  ```
* **Response (400 Bad Request — no working days in range)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Leave request must cover at least one working day (excluding weekends and public holidays)",
      "path": "/leaves/apply"
  }
  ```
* **Response (400 Bad Request — invalid leave type)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Invalid leave type. Must be CASUAL, SICK, or PRIVILEGE",
      "path": "/leaves/apply"
  }
  ```
* **Response (400 Bad Request — insufficient balance)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Insufficient leave balance. Remaining: 2, Requested: 3",
      "path": "/leaves/apply"
  }
  ```
* **Response (409 Conflict — overlapping dates)**:
  ```json
  {
      "status": 409,
      "error": "Conflict",
      "message": "Overlapping leave request detected for these dates.",
      "path": "/leaves/apply"
  }
  ```

### Get Team Leave Requests (Manager)
Retrieve leave requests from team members reporting to this manager. Supports filtering by status, employee, and date range.
* **HTTP Method**: `GET`
* **Path**: `/leaves/pending`
* **Query Parameters (Optional)**:
  - `status`: `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` (omit to return all statuses)
  - `employeeId`: `1`
  - `startDate`: `2026-06-01`
  - `endDate`: `2026-06-10`
* **Response (200 OK)**:
  ```json
  [
      {
          "id": 1,
          "employeeId": 1,
          "leaveType": "CASUAL",
          "startDate": "2026-06-01",
          "endDate": "2026-06-03",
          "numberOfDays": 3,
          "reason": "Family vacation",
          "managerId": 3,
          "status": "PENDING",
          "rejectionReason": null
      }
  ]
  ```
* **Response (403 Forbidden — caller is not a manager)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Only managers can view team leave requests",
      "path": "/leaves/pending"
  }
  ```

### Approve Leave Request (Manager)
Approve a pending leave request and deduct the leave days from the employee's balance.
* **HTTP Method**: `POST`
* **Path**: `/leaves/{id}/approve`
* **Response (200 OK)**:
  ```json
  {
      "id": 1,
      "employeeId": 1,
      "leaveType": "CASUAL",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03",
      "numberOfDays": 3,
      "reason": "Family vacation",
      "managerId": 3,
      "status": "APPROVED",
      "rejectionReason": null
  }
  ```
* **Response (403 Forbidden — caller is not a manager)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Only managers can approve leave requests",
      "path": "/leaves/1/approve"
  }
  ```
* **Response (403 Forbidden — manager is not assigned to this request)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "You are not authorized to approve this request",
      "path": "/leaves/1/approve"
  }
  ```
* **Response (400 Bad Request — request is not in PENDING status)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Only PENDING requests can be approved. Current status: APPROVED",
      "path": "/leaves/1/approve"
  }
  ```
* **Response (404 Not Found)**:
  ```json
  {
      "status": 404,
      "error": "Not Found",
      "message": "Leave request not found for ID: 1",
      "path": "/leaves/1/approve"
  }
  ```

### Reject Leave Request (Manager)
Reject a pending leave request and provide a comment.
* **HTTP Method**: `POST`
* **Path**: `/leaves/{id}/reject`
* **Request Body**:
  ```json
  {
      "comment": "Project deadlines call for all hands on deck."
  }
  ```
* **Response (200 OK)**:
  ```json
  {
      "id": 1,
      "employeeId": 1,
      "leaveType": "CASUAL",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03",
      "numberOfDays": 3,
      "reason": "Family vacation",
      "managerId": 3,
      "status": "REJECTED",
      "rejectionReason": "Project deadlines call for all hands on deck."
  }
  ```
* **Response (403 Forbidden — caller is not a manager)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Only managers can reject leave requests",
      "path": "/leaves/1/reject"
  }
  ```
* **Response (403 Forbidden — manager is not assigned to this request)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "You are not authorized to reject this request",
      "path": "/leaves/1/reject"
  }
  ```
* **Response (400 Bad Request — request is not in PENDING status)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Only PENDING requests can be rejected. Current status: APPROVED",
      "path": "/leaves/1/reject"
  }
  ```
* **Response (404 Not Found)**:
  ```json
  {
      "status": 404,
      "error": "Not Found",
      "message": "Leave request not found for ID: 1",
      "path": "/leaves/1/reject"
  }
  ```

### View Leave History (Employee)
View leave application history with pagination and status filtering.
* **HTTP Method**: `GET`
* **Path**: `/leaves/history`
* **Query Parameters (Optional)**:
  - `status`: `ALL` (default) / `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED`
  - `page`: `0` (default page index)
  - `size`: `10` (default page size)
* **Response (200 OK)**:
  ```json
  {
      "content": [
          {
              "id": 1,
              "employeeId": 1,
              "leaveType": "CASUAL",
              "startDate": "2026-06-01",
              "endDate": "2026-06-03",
              "numberOfDays": 3,
              "reason": "Family vacation",
              "managerId": 3,
              "status": "APPROVED",
              "rejectionReason": null
          }
      ],
      "pageable": {
          "pageNumber": 0,
          "pageSize": 10,
          "sort": { "empty": false, "sorted": true, "unsorted": false }
      },
      "totalPages": 1,
      "totalElements": 1,
      "size": 10,
      "number": 0,
      "first": true,
      "last": true
  }
  ```
  > **Note**: Results are sorted by `startDate` descending.

### Cancel Leave Request (Employee)
Cancel a request while it is still in `PENDING` status.
* **HTTP Method**: `POST`
* **Path**: `/leaves/{id}/cancel`
* **Response (200 OK)**:
  ```json
  {
      "id": 1,
      "employeeId": 1,
      "leaveType": "CASUAL",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03",
      "numberOfDays": 3,
      "reason": "Family vacation",
      "managerId": 3,
      "status": "CANCELLED",
      "rejectionReason": null
  }
  ```
* **Response (403 Forbidden — caller does not own this request)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "You are not authorized to cancel this request",
      "path": "/leaves/1/cancel"
  }
  ```
* **Response (400 Bad Request — request is not in PENDING status)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Only PENDING requests can be cancelled. Current status: APPROVED",
      "path": "/leaves/1/cancel"
  }
  ```
* **Response (404 Not Found)**:
  ```json
  {
      "status": 404,
      "error": "Not Found",
      "message": "Leave request not found for ID: 1",
      "path": "/leaves/1/cancel"
  }
  ```

---

## 3. Employee Service
*All endpoints below require the `Authorization: Bearer <TOKEN>` header.*

### Get Employee Profile
Fetch employee record details.
* **HTTP Method**: `GET`
* **Path**: `/employees/{id}`
* **Response (200 OK)**:
  ```json
  {
      "id": 1,
      "username": "employee1",
      "fullName": "Employee One",
      "email": "employee1@company.com",
      "role": "EMPLOYEE",
      "managerId": 3
  }
  ```
* **Response (403 Forbidden — employee requesting another user's profile)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Access denied. Employees can only access their own data.",
      "path": "/employees/2"
  }
  ```
* **Response (403 Forbidden — manager requesting a non-team member's profile)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Access denied. Managers can only access their own or their team members' data.",
      "path": "/employees/5"
  }
  ```
* **Response (404 Not Found)**:
  ```json
  {
      "status": 404,
      "error": "Not Found",
      "message": "Employee not found for ID: 1",
      "path": "/employees/1"
  }
  ```

### Get Team List (Manager Only)
Retrieve the list of employees reporting to this manager.
* **HTTP Method**: `GET`
* **Path**: `/employees/team`
* **Response (200 OK)**:
  ```json
  [
      {
          "id": 1,
          "username": "employee1",
          "fullName": "Employee One",
          "email": "employee1@company.com",
          "role": "EMPLOYEE",
          "managerId": 3
      },
      {
          "id": 2,
          "username": "employee2",
          "fullName": "Employee Two",
          "email": "employee2@company.com",
          "role": "EMPLOYEE",
          "managerId": 3
      }
  ]
  ```
* **Response (403 Forbidden — caller is not a manager)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Access denied. Only managers can view team members.",
      "path": "/employees/team"
  }
  ```

### Create Employee (Manager Only)
Creates a new employee record and fires RabbitMQ initialization triggers (to provision leave balances via the notification service).
* **HTTP Method**: `POST`
* **Path**: `/employees`
* **Request Body**:
  ```json
  {
      "id": 4,
      "username": "employee3",
      "fullName": "Employee Three",
      "email": "employee3@company.com",
      "role": "EMPLOYEE",
      "managerId": 3
  }
  ```
  > **Important**: The `id` field must match the **User ID from the Authentication Service**. The Employee Service does not auto-generate IDs — it will reject the request if the `id` already exists.

* **Response (201 Created)**:
  ```json
  {
      "id": 4,
      "username": "employee3",
      "fullName": "Employee Three",
      "email": "employee3@company.com",
      "role": "EMPLOYEE",
      "managerId": 3
  }
  ```
* **Response (403 Forbidden — caller is not a manager)**:
  ```json
  {
      "status": 403,
      "error": "Forbidden",
      "message": "Only managers can create employees",
      "path": "/employees"
  }
  ```
* **Response (400 Bad Request — employee ID already exists)**:
  ```json
  {
      "status": 400,
      "error": "Bad Request",
      "message": "Employee with ID 4 already exists",
      "path": "/employees"
  }
  ```
