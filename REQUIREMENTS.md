# TechQuarter Corporate Booking Tool Assessment

## Requirements
TechQuarter expects to process many booking requests and resource updates per second 
(aim for a peak traffic of 100 tps to cover us for the next 2 years). We would like you to build a Workflow Service 
which consumes the following customer requests via an endpoint:
1.	Register Employee (initial setup for a new user of the booking tool).
2.	Search for Booking Options (e.g., flight or hotel based on criteria).
3.	Create New Booking (e.g., reserving a flight or hotel for an employee).
4.	Appointment in hotel booking

The workflow service should validate and then process those messages in some way and deliver a response back to 
a frontend containing the processed information/results based on the consumed message. Please implement this on AWS in 
Java with React Native in mind as the UI technology.

### Message Consumption, Processor, and Frontend

- Message Consumption: The goal is to expose an endpoint which can consume workflow requests (e.g., a REST endpoint).
- Message Processor: The goal is to process messages received from the message consumption endpoint.
- Message Frontend: The goal is to render the data from the output of the other two components. Your options here may 
include a list of upcoming bookings, a calendar view of reserved resources, or a global map with real-time flight 
tracking. Consider how this hooks up to the UI React Native code in a way that keeps view and controller code separated.

Here is an example (greatly simplified for convenience) of a Create New Booking message that will be 
POSTed to your application:
```
{
"employeeId": "EMP9876",
"resourceType": "Flight",
"destination": "NYC",
"departureDate": "2024-11-05 08:00:00",
"returnDate": "2024-11-08 18:00:00",
"travelerCount": 1,
"costCenterRef": "CC-456",
"tripPurpose": "Client meeting - Acme Corp"
}
```

## How to Impress
We look for the following:
•	Can you write clean, readable, reusable, secure, and maintainable code?
•	Do you have a good command over Object-Oriented Programming (OOP) and design patterns?
•	Have you considered how automation should be built to allow rapid build, test, deploy, end-to-end test, 
monitoring, and alerting?
•	Your approach to writing tests; you don't need to cover all your code with tests, just provide a sample 
and describe your overall approach and how it would be applied comprehensively.

## How to Submit

Once you're ready, please share the following details via email:
1.	The endpoint we should POST messages to during our review process.
2.	The frontend URL we should load to view the output.
3.	The public GitHub repository where we can review your code (including a one-page document briefly outlining your 
approach to the project in the README.md in the GitHub repo). If hosted in a private Github repo please provide 
access to the specified review accounts.
4.	A short outline (max 500 words - 1 A4 page font size 12) of the approach you would recommend for leveraging 
AI Tooling to maximize your velocity in designing and delivering one or more of the following:
a. The back end services code, including unit and integration tests.
b. The Infrastructure as Code (IaC) for creating and monitoring the AWS infrastructure required for a typical 
Enterprise set of services that support a customer UI.
c. The UI using React Native with considerations outlined above in the "Message Frontend" section, 
including automated UI & API Testing.
