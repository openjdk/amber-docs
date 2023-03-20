package example;

import org.json.*;

import java.lang.StringTemplate.SimpleProcessor;
import java.lang.StringTemplate.StringProcessor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static example.Mailer.TemplateFields.*;

public class Mailer {
    static final SimpleProcessor<JSONArray> JSON = ts -> new JSONArray(ts.interpolate());
    static final JSONArray mailingList = JSON."""
	[
		{
			  "FIRSTNAME": "John",
			  "INITIAL":   "H",
			  "SURNAME":   "Jones",
			  "ADDRESS":   "123 Maple St.",
			  "CITY":      "AnyTown",
			  "STATE":     "XY",
			  "COUNTRY":   "SomeWhere",
			  "POSTAL":    "XYZ-123"
		},
		{
			  "FIRSTNAME": "Mary",
			  "INITIAL":   "S",
			  "SURNAME":   "Elephant",
			  "ADDRESS":   "78 Oak St.",
			  "CITY":      "AnyTown",
			  "STATE":     "XY",
			  "COUNTRY":   "SomeWhere",
			  "POSTAL":    "XYZ-123"
		}
	]
	""";

    public enum TemplateFields {
        EMAIL,
        DATE,
        FIRSTNAME,
        INITIAL,
        SURNAME,
        ADDRESS,
        CITY,
        STATE,
        COUNTRY,
        POSTAL;
    }

    public static String date() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        return dtf.format(LocalDateTime.now());
    }

    public static void main(String[] args) {
        for (Object object: mailingList) {
            JSONObject reciprient = (JSONObject)object;
            StringProcessor processor = ts -> {
                List<Object> values = ts.values()
                        .stream()
                        .map(v -> v instanceof TemplateFields tf ? reciprient.get(tf.toString()) : v)
                        .toList();
                return StringTemplate.interpolate(ts.fragments(), values);
            };
            var result = processor."""
                    Department of Agriculture

                    \{date()}

                    \{FIRSTNAME} \{INITIAL}. \{SURNAME}
                    \{ADDRESS}
                    \{CITY}, \{STATE}, \{COUNTRY}
                    \{POSTAL}

                    Dear \{FIRSTNAME},

                    I wish to inform you that your application for
                    a sunflower seed permit has been approved. You
                    should receive said permit within three weeks.
                    Do not hesitate to enquire on any issue.

                    Sincerely,

                    Joan Smith
                    Director
                    """;
            System.out.println(result);
        }
    }
}
