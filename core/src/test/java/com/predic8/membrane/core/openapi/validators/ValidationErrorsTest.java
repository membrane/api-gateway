package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.Assert.*;

public class ValidationErrorsTest {

    @Test
    public void testToString() {
        
        ValidationErrors ve = new ValidationErrors();
        
        ValidationContext ctx1 = new ValidationContext()
                .validatedEntityType(BODY)
                .complexType("Foo")
                .addJSONpointerSegment("foo")
                .addJSONpointerSegment("bar");
        ve.add(new ValidationError(ctx1,"Doofe Katastrophe!"));

        ValidationContext ctx2 = new ValidationContext()
                .validatedEntityType(BODY)
                .schemaType("integer")
                .addJSONpointerSegment("foo")
                .addJSONpointerSegment("bar");
        ve.add(new ValidationError(ctx2,"Noch ne Katastrophe!"));

        ValidationContext ctx3 = new ValidationContext()
                .validatedEntityType(BODY)
                .addJSONpointerSegment("foo")
                .addJSONpointerSegment("baz");
        ve.add(new ValidationError(ctx3,"Doofe Katastrophe!"));

        System.out.println("ve = " + ve);
        
    }
}