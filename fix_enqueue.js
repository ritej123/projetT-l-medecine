const fs = require('fs');
const file = 'c:/projettélémedecine2/backend/src/main/java/com/telemed/platform/notification/service/DoctorStatusNotificationService.java';
let code = fs.readFileSync(file, 'utf8');

const target = `    private void enqueue(Doctor doctor, NotificationType type, String body) {
        notificationRepository.save(new AppointmentEmailNotification(
                UUID.randomUUID(),
                doctor.getUser().getEmail(),
                type,
                clock.instant(),
                null,
                (int) (System.currentTimeMillis() % 1000000000),
                body));
    }`;

const replacement = `    private void enqueue(Doctor doctor, NotificationType type, String body) {
        notificationRepository.save(new AppointmentEmailNotification(
                doctor.getUser().getEmail(),
                type,
                clock.instant(),
                null,
                (int) (System.currentTimeMillis() % 1000000000),
                body));
    }`;

if (code.includes(target)) {
  code = code.replace(target, replacement);
} else {
  const targetCRLF = target.replace(/\n/g, '\r\n');
  const replacementCRLF = replacement.replace(/\n/g, '\r\n');
  code = code.replace(targetCRLF, replacementCRLF);
}

fs.writeFileSync(file, code, 'utf8');
console.log('Fixed enqueue in DoctorStatusNotificationService');
