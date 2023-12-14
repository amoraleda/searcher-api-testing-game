package org.example;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearcherEndpointTest {

  private static final String ENDPOINT = "http://143.47.57.29/searcher";
  private static final int OK = 200;
  private static final int DEFAULT_PAGE_SIZE = 5;

  @Test
  void get_default_content_match_default_page_size() {
    getWithParams("")
      .body("content", hasSize(DEFAULT_PAGE_SIZE))
      .body("size", equalTo(DEFAULT_PAGE_SIZE))
      .body("numberOfElements", equalTo(DEFAULT_PAGE_SIZE))
      .body("pageable.pageSize", equalTo(DEFAULT_PAGE_SIZE));
  }

  @Test
  void empty_filters_get_same_results() {
    JsonPath jsonPath = getWithParams("").extract().jsonPath();
    int numberOfElements = jsonPath.getInt("numberOfElements");

    getWithParams("?modelName=&brands=&years&types=&fuels=&transmissions=&")
      .body("numberOfElements", equalTo(numberOfElements))
      .body("content", hasSize(DEFAULT_PAGE_SIZE))
      .body("size", equalTo(DEFAULT_PAGE_SIZE))
      .body("numberOfElements", equalTo(DEFAULT_PAGE_SIZE))
      .body("pageable.pageSize", equalTo(DEFAULT_PAGE_SIZE));
  }

  @Nested
  class FilteringTest {

    @ParameterizedTest
    @ValueSource(strings = {"Tesla", "TESLA", "tESLA", "tesla"})
    void get_model_names_starting_with_case_insensitive(String modelName) {

      getWithParams("?modelName=" + modelName)
        .body("content.name", everyItem(startsWith("Tesla")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACURA", "ALFA_ROMEO", "AUDI", "BMW", "CHEVROLET", "CHRYSLER", "CITROEN", "DACIA", "FIAT", "FORD", "GMC",
      "HONDA", "HYUNDAI", "INFINITI", "JAGUAR", "JEEP", "KIA", "LAND_ROVER", "LEXUS", "MAZDA", "MERCEDES_BENZ", "MITSUBISHI", "NISSAN",
      "PEUGEOT", "PORSCHE", "RENAULT", "SEAT", "SMART", "SUBARU", "SUZUKI", "TESLA", "VOLKSWAGEN", "VOLVO", "TOYOTA"})
    void get_brands_with_exact_name(String brand) {

      getWithParams("?brands=" + brand)
        .body("content.brand", everyItem(equalTo(brand)));
      boolean last;
      int page = 0;
      do {
        Response response = get(ENDPOINT + "?brands=" + brand + "&page=" + page);
        response.then().body("content.brand", everyItem(equalTo(brand)));
        JsonPath jsonPath = response.jsonPath();
        last = jsonPath.getBoolean("last");
        page++;
      } while (!last);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELECTRIC", "COUPE", "HATCHBACK", "MINIVAN", "OFF_ROAD", "SEDAN", "SUV", "TRUCK", "VAN", "WAGON"})
    void get_brands_with_exact_type(String type) {
      boolean last;
      int page = 0;
      do {
        Response response = get(ENDPOINT + "?types=" + type + "&page=" + page);
        response.then().body("content.type", everyItem(equalTo(type)));
        JsonPath jsonPath = response.jsonPath();
        last = jsonPath.getBoolean("last");
        page++;
      } while (!last);
    }


    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"DIESEL", "ELECTRIC", "GASOLINE", "HYBRID", "PLUGIN_HYBRID"})
    void get_fuels_with_exact_name(String fuel) {
      boolean last;
      int page = 0;
      do {
        Response response = get(ENDPOINT + "?fuels=" + fuel + "&page=" + page);
        response.then().body("content.fuels", everyItem(equalTo(fuel)));
        JsonPath jsonPath = response.jsonPath();
        last = jsonPath.getBoolean("last");
        page++;
      } while (!last);
    }

    @ParameterizedTest
    @ValueSource(ints = {25000, 30000, 50000, 100000})
    void get_below_or_equals_than_max_price_upper_limit(int maxPriceUpperLimit) {
      boolean last;
      int page = 0;
      boolean hasSomeDifferent = true;

      do {
        Response response = get(ENDPOINT + "?maxPriceUpperLimit=" + maxPriceUpperLimit + "&page=" + page);
        JsonPath jsonPath = response.jsonPath();
        last = jsonPath.getBoolean("last");

        hasSomeDifferent &= jsonPath.getList("content.priceMax", Integer.class).stream()
          .anyMatch(priceMax -> priceMax != maxPriceUpperLimit);

        response
          .then()
          .statusCode(OK)
          .body("content.priceMax", everyItem(lessThanOrEqualTo(maxPriceUpperLimit)));

        page++;
      } while (!last);

      assertTrue(hasSomeDifferent, "All elements are the same value! This is fishy!");
    }

    @ParameterizedTest
    @ValueSource(ints = {25000, 30000, 50000, 100000})
    void get_above_or_equals_than_min_price_lower_limit(int minPriceLowerLimit) {
      boolean last;
      int page = 0;
      boolean hasSomeDifferent = true;
      do {
        Response response = get(ENDPOINT + "?minPriceLowerLimit=" + minPriceLowerLimit + "&page=" + page);
        JsonPath jsonPath = response.jsonPath();
        last = jsonPath.getBoolean("last");

        hasSomeDifferent &= jsonPath.getList("content.priceMax", Integer.class).stream()
          .anyMatch(priceMax -> priceMax != minPriceLowerLimit);

        response
          .then()
          .statusCode(OK)
          .body("content.priceMax", everyItem(greaterThanOrEqualTo(minPriceLowerLimit)));

        page++;
      } while (!last);

      assertTrue(hasSomeDifferent, "All elements are the same value! This is fishy!");
    }

  }

  @Nested
  class PaginationTests {

    @ParameterizedTest
    @ValueSource(strings = {"ELECTRIC", "SEDAN", "WAGON"})
    void check_first_page(String types) {
      int page = 0;
      int size = 10;

      getWithParams("?types=" + types + "&page=" + page + "&size=" + size)
        .body("first", equalTo(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELECTRIC", "SEDAN", "WAGON"})
    void page_empty_when_over_total_pages(String types) {
      int page = 0;
      int size = 10;
      JsonPath jsonPath = get(ENDPOINT + "?types=" + types + "&page=" + page + "&size=" + size).jsonPath();
      int totalPages = jsonPath.getInt("totalPages");

      getWithParams("?types=" + types + "&page=" + totalPages + "&size=" + size)
        .body("empty", equalTo(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELECTRIC", "SEDAN", "WAGON"})
    void check_last_page(String types) {
      int page = 0;
      int size = 10;
      int totalPages = 0;
      boolean last;

      do {
        JsonPath jsonPath = get(ENDPOINT + "?types=" + types + "&page=" + page + "&size=" + size).jsonPath();
        last = jsonPath.getBoolean("last");

        if (page == 0) {
          totalPages = jsonPath.getInt("totalPages");
        }

        if (page < totalPages - 1) {
          assertFalse(jsonPath.getBoolean("last"));
        }

        page++;
      } while (!last);

      getWithParams("?types=" + types + "&page=" + (totalPages - 1) + "&size=" + size)
        .body("last", equalTo(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {25000, 30000, 50000})
    void check_total_pages(int maxPriceUpperLimit) {
      boolean last;
      int page = 0;
      int size = 10;
      int totalPages = 0;

      do {
        Response response = get(ENDPOINT + "?maxPriceUpperLimit=" + maxPriceUpperLimit + "&page=" + page + "&size=" + size);
        JsonPath jsonPath = response.jsonPath();

        last = jsonPath.getBoolean("last");

        if (page == 0) {
          totalPages = jsonPath.getInt("totalPages");
        }

        response
          .then()
          .statusCode(OK)
          .body("totalPages", equalTo(totalPages));

        page++;
      } while (!last);

      assertEquals(totalPages, page);
    }

    @ParameterizedTest
    @ValueSource(ints = {25000, 30000, 50000})
    void pagination_works(int maxPriceUpperLimit) {
      boolean last;
      int page = 0;
      int size = 10;

      do {
        Response response = get(ENDPOINT + "?maxPriceUpperLimit=" + maxPriceUpperLimit + "&page=" + page + "&size=" + size);
        JsonPath jsonPath = response.jsonPath();

        last = jsonPath.getBoolean("last");

        response
          .then()
          .statusCode(OK)
          .body("pageable.pageNumber", equalTo(page))
          .body("number", equalTo(page))
          .body("pageable.pageSize", equalTo(size))
          .body("size", equalTo(size));

        page++;
      } while (!last);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELECTRIC", "SEDAN", "WAGON"})
    void number_of_results_match(String types) {
      boolean last;
      int page = 0;
      int size = 10;
      int totalElements = 0;
      int elementsCounter = 0;

      do {
        JsonPath jsonPath = get(ENDPOINT + "?types=" + types + "&page=" + page + "&size=" + size).jsonPath();

        last = jsonPath.getBoolean("last");
        elementsCounter += jsonPath.getList("content").size();

        if (jsonPath.getBoolean("first")) {
          totalElements = jsonPath.getInt("totalElements");
        }

        if (!last) {
          assertEquals(size, jsonPath.getInt("numberOfElements"));
        } else {
          assertTrue(jsonPath.getInt("numberOfElements") <= size);
        }

        page++;
      } while (!last);

      assertEquals(totalElements, elementsCounter);
    }
  }

  @Nested
  class SortingTest {

    @Test
    void sort_by_year_desc() {
      boolean last;
      int page = 0;
      int smallestOfLastPage = 0;
      do {
        JsonPath jsonPath = get(ENDPOINT + "?sort=BY_YEAR,DESC&page=" + page).jsonPath();

        last = jsonPath.getBoolean("last");
        List<Integer> years = jsonPath.getList("content.year", Integer.class);
        assertTrue(isSorted(years, SortingTest::descendOrder));

        var biggestCurrentPage = years.get(0);

        if (page == 0) {
          smallestOfLastPage = biggestCurrentPage;
        }
        assertTrue(smallestOfLastPage >= biggestCurrentPage);

        smallestOfLastPage = getLastItem(years);

        page++;
      } while (!last);
    }

    @Test
    void sort_by_year_asc() {
      boolean last;
      int page = 0;

      int biggestOfLastPage = 0;
      do {
        JsonPath jsonPath = get(ENDPOINT + "?sort=BY_YEAR,ASC&page=" + page).jsonPath();

        last = jsonPath.getBoolean("last");
        List<Integer> years = jsonPath.getList("content.year", Integer.class);

        assertTrue(isSorted(years, SortingTest::ascendentOrder));

        var smallestCurrentPage = years.get(0);
        if (page == 0) {
          biggestOfLastPage = smallestCurrentPage;
        }
        assertTrue(biggestOfLastPage >= smallestCurrentPage);

        biggestOfLastPage = getLastItem(years);

        page++;
      } while (!last);
    }


    @Disabled
    @Test
    void sort_by_year_desc_and_price_max_asc() {
      boolean last;
      int page = 0;
      int smallestOfYearLastPage = 0;
      do {
        JsonPath jsonPath = get(ENDPOINT + "?sort=BY_YEAR,DESC&sort=BY_PRICE_MAX,ASC&page=" + page).jsonPath();

        last = jsonPath.getBoolean("last");
        List<Integer> years = jsonPath.getList("content.year", Integer.class);
        List<Integer> priceMax = jsonPath.getList("content.priceMax", Integer.class);

        assertTrue(isSorted(years, SortingTest::descendOrder));
        int finalPage = page;
        assertTrue(isSorted(priceMax, SortingTest::ascendentOrder), () -> "Falla en la " + finalPage);

        var biggestYearCurrentPage = years.get(0);

        if (page == 0) {
          smallestOfYearLastPage = biggestYearCurrentPage;
        }
        assertTrue(smallestOfYearLastPage >= biggestYearCurrentPage);

        smallestOfYearLastPage = getLastItem(years);

        page++;
      } while (!last);
    }

    @Disabled
    @Test
    void sort_by_year_asc_and_price_max_desc() {
      boolean last;
      int page = 0;

      int biggestOfLastPage = 0;
      do {
        JsonPath jsonPath = get(ENDPOINT + "?sort=BY_YEAR,ASC&sort=BY_PRICE_MAX,DESC&page=" + page).jsonPath();

        last = jsonPath.getBoolean("last");
        List<Integer> years = jsonPath.getList("content.year", Integer.class);
        List<Integer> priceMax = jsonPath.getList("content.priceMax", Integer.class);

        assertTrue(isSorted(years, SortingTest::ascendentOrder));
        int finalPage = page;
        assertTrue(isSorted(priceMax, SortingTest::descendOrder), () -> "Falla en la " + finalPage);

        var smallestCurrentPage = years.get(0);
        if (page == 0) {
          biggestOfLastPage = smallestCurrentPage;
        }
        assertTrue(biggestOfLastPage >= smallestCurrentPage);

        biggestOfLastPage = getLastItem(years);

        page++;
      } while (!last);
    }


    private static Integer getLastItem(List<Integer> years) {
      if (years.isEmpty()) {
        return -1;
      }

      return years.get(years.size() - 1);
    }

    private static int descendOrder(Integer item1, Integer item2) {
      return item1 >= item2 ? -1 : 1;
    }

    private static int ascendentOrder(Integer item1, Integer item2) {
      return item1 <= item2 ? -1 : 1;
    }
  }

  private static ValidatableResponse getWithParams(String params) {
    return get(ENDPOINT + params).then().statusCode(OK);
  }

  // Multiple values
  // Constants: fields
  // Change repeated params
  // Refactor: maybe the params

  public static boolean isSorted(List<Integer> items, Comparator<Integer> itemComparator) {
    if (items == null || items.size() == 1) {
      return true;
    }

    Iterator<Integer> iter = items.iterator();
    Integer current, previous = iter.next();
    while (iter.hasNext()) {
      current = iter.next();
      if (itemComparator.compare(previous, current) > 0) {
        return false;
      }
      previous = current;
    }
    return true;
  }
}
