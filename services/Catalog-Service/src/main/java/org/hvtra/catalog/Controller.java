package org.hvtra.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class Controller {
    @GetMapping()
    public String getCatalog() {
        return "This is the Catalog Service";
    }
}
