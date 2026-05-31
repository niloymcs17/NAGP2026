# Postman Collection Documentation: NAGP Leave Portal

This document provides a step-by-step guide to import, configure, and execute the API requests in the **NAGP Leave Portal** Postman Collection.

The collection is located in your workspace root: 
👉 [Leave_Portal.postman_collection.json](../Leave_Portal.postman_collection.json)

---

## 1. Prerequisites
Before running the APIs, ensure your backend microservices are running. 

If running locally:
1. Start your local RabbitMQ instance.
2. Start the **Eureka Server** first (port `8761`).
3. Start the remaining services (`authentication-service`, `employee-service`, `leave-management-service`, `notification-service`, and `api-gateway`).

All requests in the collection route through the **API Gateway** running at `http://localhost:8080`.

---

## 2. Importing the Collection to Postman
1. Open **Postman**.
2. Click the **Import** button in the top-left panel.
3. Select **Files** or drag-and-drop the `Leave_Portal.postman_collection.json` file from your workspace folder.
4. Click **Import** to confirm.

---

## 3. Collection Variables
The collection uses predefined variables to make switching profiles easy. To view or edit them:
1. Click on the collection name **"NAGP Leave Portal"** in the left sidebar.
2. Go to the **Variables** tab.

* **`baseUrl`**: Mapped to `http://localhost:8080` (API Gateway URL).
* **`jwt_token`**: Handled automatically. The Login requests save the token here.
* **`leaveRequestId`**: Set this manually (e.g. to `1`) when testing cancellation, approval, or rejection.

---

## 4. Step-by-Step API Execution Workflow

### Step 1: Authenticate (Get JWT Token)
1. Expand the **1. Authentication** folder.
2. Open and run **Login - Employee 1** (or **Login - Manager 1**).
3. The response will return a token. Postman will automatically run this test script:
   ```javascript
   var jsonData = pm.response.json();
   if (jsonData.token) {
       pm.collectionVariables.set("jwt_token", jsonData.token);
   }
   ```
   *This automatically saves the token to the `{{jwt_token}}` variable for all subsequent requests.*

---

### Step 2: Employee Operations
Double-click and open the **2. Leave Operations (Employee)** folder. Ensure you are logged in as **Employee 1** for these requests:

1. **Get Leave Balances** (`GET /leaves/balances`)
   - Retrieves the allocated, used, and remaining days for Casual (12), Sick (10), and Privilege (15) leaves.
2. **Apply for Leave** (`POST /leaves/apply`)
   - Sends a request for 3 Casual days reporting to manager ID `3`.
   - Validates that you have enough remaining days and that dates don't overlap with existing requests.
   - Triggers a console log event in the **`notification-service`**.
3. **View Leave History** (`GET /leaves/history`)
   - Retrieves list of leaves with query parameters for filtering (`status=ALL`) and pagination (`page=0`, `size=10`).
4. **Cancel Pending Request** (`POST /leaves/{{leaveRequestId}}/cancel`)
   - Cancels a leave request that is still in `PENDING` status.

---

### Step 3: Manager Operations
To test approvals/rejections, you must authenticate as **Manager 1**:
1. Run **Login - Manager 1** in the Authentication folder (this updates `{{jwt_token}}` to the manager's token).
2. Open the **3. Leave Management (Manager)** folder.

1. **Get Team Pending Requests** (`GET /leaves/pending?status=PENDING`)
   - Retrieves pending requests from team members reporting to manager ID `3`.
2. **Approve Leave Request** (`POST /leaves/{{leaveRequestId}}/approve`)
   - Note: Set the variable `leaveRequestId` in your collection variables (or replace the URL parameter manually) to match the ID of the request you want to approve.
   - Deducts the requested days from the employee's balance and marks the status as `APPROVED`.
3. **Reject Leave Request** (`POST /leaves/{{leaveRequestId}}/reject`)
   - Submits comments in the JSON body (e.g. `{"comment": "Business conflicts"}`).
   - Sets the status to `REJECTED` and logs the reason.

---

### Step 4: Employee Directory (Access Control Checks)
Open the **4. Employee Directory** folder to verify Role-Based Access Control:

1. **Get Employee Profile** (`GET /employees/1`)
   - If logged in as **Employee 1**, you can view your profile.
   - If logged in as **Employee 1** and you try to fetch `/employees/2`, the API Gateway returns a `403 Forbidden` error.
   - If logged in as **Manager 1**, you can retrieve profiles for yourself or your team members.
2. **Get Team (Manager Only)** (`GET /employees/team`)
   - Retrieves all employees reporting to you. If called with an Employee token, returns `403 Forbidden`.
3. **Create Employee (Manager Only)** (`POST /employees`)
   - Creates a new employee profile. 
   - Triggers the RabbitMQ `employee.created` event, which the **`leave-management-service`** consumes to automatically allocate default leave balances (12, 10, 15 days) for the new employee.
