import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@DataMapping(value = "events", type = DataMapping.Type.ARRAY_DIR)
public record Events(List<Event> list) {

    public Events {
        list.sort(Comparator.comparing(Event::parsedDate).reversed());
    }

    public record Event(String title, String description, String date, String link) {

        public LocalDate parsedDate() {
            return LocalDate.parse(date);
        }
    }
}
