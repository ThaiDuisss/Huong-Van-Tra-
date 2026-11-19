package org.hvtra.catalog.taste;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequiredArgsConstructor
public class TasteSnapshotController {
    private final TasteSnapshotAuth auth;
    private final TasteSnapshotService service;

    @GetMapping(TasteSnapshotAuth.PATH)
    public ResponseEntity<TasteSnapshotService.Snapshot> snapshot(HttpServletRequest request) {
        String nonce = auth.authenticate(request);
        var snapshot = service.read();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(TasteSnapshotAuth.SIGNATURE, auth.signResponse(nonce, snapshot)).body(snapshot);
    }
}
