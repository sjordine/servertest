package com.servertest.products;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end API tests against the dev Postgres, in the isolated
 * {@code products_test} schema. Every test rolls back.
 * Every request carries a mock JWT with read + write scopes (an editor);
 * the security rules themselves are covered in {@link SecurityRulesTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogApiTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    String unitUn;
    String unitL;

    @BeforeEach
    void seedUnits() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_products:read"),
                        new SimpleGrantedAuthority("SCOPE_products:write"))))
                .build();

        // V2 seeds the standard units; look up their ids.
        JsonNode units = read(mvc.perform(get("/api/products/units")).andExpect(status().isOk()));
        for (JsonNode u : units) {
            switch (u.get("code").asText()) {
                case "un" -> unitUn = u.get("id").asText();
                case "l" -> unitL = u.get("id").asText();
                default -> { }
            }
        }
    }

    // ---------- units ----------

    @Test
    void createsUnitAndRejectsDuplicateCode() throws Exception {
        send(post("/api/products/units"), Map.of("code", " Box ", "name", "Box"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("box"));
        send(post("/api/products/units"), Map.of("code", "BOX", "name", "Another box"))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedUnitIsHiddenAndCantBeUsedForNewPackages() throws Exception {
        String unit = id(send(post("/api/products/units"), Map.of("code", "cx", "name", "Caixa")));
        String product = createProduct("MILK-WH-1L", null);

        mvc.perform(delete("/api/products/units/" + unit)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/units"))
                .andExpect(jsonPath("$[*].code", not(hasItem("cx"))));
        mvc.perform(get("/api/products/units?includeArchived=true"))
                .andExpect(jsonPath("$[*].code", hasItem("cx")));

        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unit, "quantity", 6))
                .andExpect(status().isConflict());
    }

    // ---------- categories ----------

    @Test
    void categoryGroupsProducts() throws Exception {
        String category = id(send(post("/api/products/categories"), Map.of("name", "Whole milk")));
        send(post("/api/products/categories"), Map.of("name", "WHOLE MILK")).andExpect(status().isConflict());

        createProduct("MILK-WH-1L", category);
        createProduct("MILK-WH-05L", category);
        createProduct("RICE-WHITE", null);

        mvc.perform(get("/api/products?categoryId=" + category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].sku").value("MILK-WH-05L"))
                .andExpect(jsonPath("$.items[0].category.name").value("Whole milk"));
    }

    // ---------- products ----------

    @Test
    void createsProductWithNormalisedSkuAndContent() throws Exception {
        send(post("/api/products"), Map.of("sku", " milk-wh-1l ", "description", "Whole milk 1 l bottle",
                "contentQuantity", 1, "contentUnitId", unitL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("MILK-WH-1L"))
                .andExpect(jsonPath("$.content.label").value("1 l"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.packages.length()").value(0));

        mvc.perform(get("/api/products/sku/milk-wh-1l"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Whole milk 1 l bottle"));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCase() throws Exception {
        createProduct("RICE-WHITE", null);
        send(post("/api/products"), Map.of("sku", "rice-white", "description", "dup"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidProduct() throws Exception {
        send(post("/api/products"), Map.of("sku", " ", "description", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.description").exists());
        // content needs both quantity and unit
        send(post("/api/products"), Map.of("sku", "X-1", "description", "x", "contentQuantity", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchesBySkuOrDescription() throws Exception {
        createProduct("MILK-WH-1L", null);
        createProduct("RICE-WHITE", null);
        mvc.perform(get("/api/products?q=rice"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("RICE-WHITE"));
    }

    @Test
    void updateKeepsSku() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(put("/api/products/" + product), Map.of("description", "Whole milk, 1 l bottle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("MILK-WH-1L"))
                .andExpect(jsonPath("$.description").value("Whole milk, 1 l bottle"));
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/products/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/not-a-uuid")).andExpect(status().isBadRequest());
    }

    // ---------- packages ----------

    @Test
    void generatesPackageCodeWhenMissing() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MILK-WH-1L-6UN"))
                .andExpect(jsonPath("$.label").value("6 un"))
                .andExpect(jsonPath("$.product.sku").value("MILK-WH-1L"));

        mvc.perform(get("/api/products/" + product))
                .andExpect(jsonPath("$.packages.length()").value(1));
        mvc.perform(get("/api/products?q=MILK"))
                .andExpect(jsonPath("$.items[0].packageCount").value(1));
    }

    @Test
    void customPackageCodeIsNormalisedAndUnique() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(post("/api/products/" + product + "/packages"),
                Map.of("code", "box-6", "unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BOX-6"));
        send(post("/api/products/" + product + "/packages"),
                Map.of("code", "BOX-6", "unitId", unitUn, "quantity", 12))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSameMeasureTwiceOnAProduct() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated());
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6.0))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidPackage() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
        send(post("/api/products/00000000-0000-0000-0000-000000000000/packages"),
                Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isNotFound());
    }

    @Test
    void packageCodeIsNotReusedAfterArchiving() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        String pkg = id(send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6)));
        mvc.perform(delete("/api/products/" + product + "/packages/" + pkg)).andExpect(status().isNoContent());

        // Same measure is allowed again once the old one is archived, but it gets a new code.
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MILK-WH-1L-6UN-2"));
        // Restoring the old one would now duplicate the active measure.
        mvc.perform(post("/api/products/" + product + "/packages/" + pkg + "/restore"))
                .andExpect(status().isConflict());
    }

    // ---------- archiving ----------

    @Test
    void archivingProductArchivesPackagesButKeepsLookups() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/products/" + product)).andExpect(status().isNoContent());

        mvc.perform(get("/api/products")).andExpect(jsonPath("$.items[*].sku", not(hasItem("MILK-WH-1L"))));
        mvc.perform(get("/api/products?includeArchived=true"))
                .andExpect(jsonPath("$.items[*].sku", hasItem("MILK-WH-1L")));
        mvc.perform(get("/api/products/sku/MILK-WH-1L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mvc.perform(get("/api/products/packages/MILK-WH-1L-6UN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.product.archived").value(true));

        // An archived product takes no new packages, and its SKU can't be reused.
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 12))
                .andExpect(status().isConflict());
        send(post("/api/products"), Map.of("sku", "MILK-WH-1L", "description", "again"))
                .andExpect(status().isConflict());
    }

    @Test
    void restoringProductRestoresPackagesArchivedWithIt() throws Exception {
        String product = createProduct("MILK-WH-1L", null);
        String single = id(send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 1)));
        send(post("/api/products/" + product + "/packages"), Map.of("unitId", unitUn, "quantity", 6))
                .andExpect(status().isCreated());

        // Archived on its own, before the product: should stay archived after the product is restored.
        mvc.perform(delete("/api/products/" + product + "/packages/" + single)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/products/" + product)).andExpect(status().isNoContent());

        mvc.perform(post("/api/products/" + product + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(get("/api/products/" + product + "/packages"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("MILK-WH-1L-6UN"));
    }

    // ---------- helpers ----------

    private String createProduct(String sku, String categoryId) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("sku", sku, "description", sku + " description"));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        return id(send(post("/api/products"), body).andExpect(status().isCreated()));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            Object body) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private String id(ResultActions result) throws Exception {
        return read(result).get("id").asText();
    }
}
