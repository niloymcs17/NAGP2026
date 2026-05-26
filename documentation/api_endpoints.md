# API Endpoint Documentation

This document provides a comprehensive catalog of the REST APIs exposed by the ** Leave Portal**, routing through the API Gateway at `http://localhost:8080`.

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
  ```text
  Invalid username or password
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
      "numberOfDays": 3,
      "reason": "Family vacation",
      "managerId": 3
  }
  ```
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
* **Response (400 Bad Request - Date Ranges)**:
  ```text
  Start date cannot be in the past
  ```
* **Response (400 Bad Request - Balance)**:
  ```text
  Insufficient leave balance. Remaining: 2, Requested: 3
  ```
* **Response (409 Conflict - Overlapping dates)**:
  ```text
  Overlapping leave request detected for these dates.
  ```

### Get Team Pending Requests (Manager)
Retrieve pending leave requests from team members reporting to this manager.
* **HTTP Method**: `GET`
* **Path**: `/leaves/pending`
* **Query Parameters (Optional)**:
  - `status`: `PENDING` / `APPROVED` / `REJECTED`
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
* **Response (403 Forbidden - Requesting another user's profile)**:
  ```text
  Access denied. Employees can only access their own data.
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

### Create Employee (Manager Only)
Creates a new employee record and fires RabbitMQ initialization triggers.
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
