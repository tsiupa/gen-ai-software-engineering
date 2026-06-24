A simple REST API that registers a person in a queue for a doctor's appointment.
The patient client sends a request with their name and a description of the reason for the doctor's visit.
The response returns a 30-minute time slot for the visit and a ticket number.
Scheduling runs from 9 AM to 4 PM.
The doctor client requests the first client in the queue and removes them from the queue.