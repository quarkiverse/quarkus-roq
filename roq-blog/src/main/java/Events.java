import java.time.LocalDate;
import java.util.List;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "events", parentArray = true)
public record Events(List<Event> list) {

    public record Event(String title, String description, String date) {

        public LocalDate parsedDate() {
            return LocalDate.parse(date);
        }
    }
}
