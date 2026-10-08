# User stories

Personas: **Alice** (patient), **Dr Martin** (general practitioner), **Sabine** (secretariat / admin).

1. **Find a slot** — As a patient, I want to see the free slots of a practitioner for a week, so that I can choose a time.
   - Given Dr Martin works Monday 09:00–12:00 in 30-minute slots, When I open the week, Then I see 09:00, 09:30 … 11:30 except slots already taken or in the past.

2. **Book an appointment** — As a patient, I want to book a slot in one click.
   - Given a free slot, When I confirm, Then I get "EN ATTENTE" immediately (202) and the slot disappears for other patients.

3. **Follow the confirmation** — As a patient, I want to see my booking become confirmed without refreshing.
   - Given a pending booking, When patient-service validates my record, Then the status shown switches to "CONFIRMÉ" within seconds (polling every 2 s).

4. **Be notified** — As a patient, I want a message when my appointment is confirmed, refused, moved or cancelled.
   - Given my appointment is confirmed, When I open "Notifications", Then I read "votre rendez-vous du … est confirmé" with the Paris date and time.

5. **No double booking** — As a practitioner, I never want two patients on the same slot.
   - Given a slot pending or confirmed, When another patient books it, Then they get 409 "Ce créneau n'est pas disponible"; the database also refuses any overlapping active appointment.

6. **Inactive patient refused** — As the secretariat, I want bookings from deactivated records refused.
   - Given Jean Moreau is INACTIVE, When a booking is made for him, Then it ends REJECTED, the slot is released and a notification explains why.

7. **Saga timeout** — As the practice, I don't want slots blocked forever if a service is down.
   - Given a booking pending for more than 2 minutes, When the timeout job runs, Then it becomes REJECTED with reason VALIDATION_TIMEOUT and the slot is free again.

8. **See my agenda** — As a practitioner, I want a week view of my appointments.
   - Given confirmed bookings, When I open "Agenda", Then I see them per day with the patient name, time, reason and status, refreshed every 10 s.

9. **Cancel an appointment** — As a practitioner, I want to cancel and have the patient informed.
   - Given a confirmed appointment, When I cancel choosing a reason in the list (and an optional internal comment), Then it becomes ANNULÉ and the patient receives "annulé par votre praticien (indisponibilité du praticien)"; the comment is never sent.

10. **Manage patient records** — As the secretariat, I want to list and create patients.
    - Given valid data, When I create a patient, Then it appears ACTIVE in the list; Given an e-mail already used, Then I get a conflict message.

11. **Deactivate a patient** — As the secretariat, I want to deactivate a record and free their future slots.
    - Given Alice has future appointments, When I deactivate her record, Then they are all cancelled (cancelledBy SYSTEM) and she is notified.

12. **Privacy by design** — As the DPO, I want health-related personal data to stay in its service.
    - Given any Kafka event, When I read it, Then it contains identifiers and dates only, never a name, e-mail or phone.

13. **Trace a booking** — As an SRE, I want to follow one booking across services.
    - Given the observability profile, When I search Tempo for the gateway `POST` span, Then the trace contains spans from gateway, scheduling, patient and notification services.
