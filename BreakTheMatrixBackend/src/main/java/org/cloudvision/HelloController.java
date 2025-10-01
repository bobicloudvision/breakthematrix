package org.cloudvision;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "General", description = "General application endpoints")
public class HelloController {

    @Operation(summary = "Hello World", description = "Simple hello world endpoint to test API connectivity.")
    @ApiResponse(responseCode = "200", description = "Hello message returned successfully")
    @GetMapping("/")
    public String hello() {
        return "Hello, world";
    }
}


