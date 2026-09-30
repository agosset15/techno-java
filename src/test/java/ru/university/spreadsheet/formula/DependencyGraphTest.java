package ru.university.spreadsheet.formula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.model.CellRef;

@DisplayName("DependencyGraph: граф зависимостей ячеек")
class DependencyGraphTest {

    private final DependencyGraph graph = new DependencyGraph();

    private static CellRef ref(String a1) {
        return CellRef.parse(a1);
    }

    /** Регистрирует формулу ячейки dependent, ссылающуюся на указанные ячейки. */
    private void formula(String dependent, String... precedents) {
        for (String precedent : precedents) {
            graph.addDependency(ref(dependent), ref(precedent));
        }
    }

    private List<String> order(String changed) {
        return graph.topologicalOrder(ref(changed)).stream().map(CellRef::toA1).toList();
    }

    @Test
    @DisplayName("прямой цикл: A1 = B1, B1 = A1")
    void directCycle() {
        formula("A1", "B1");
        formula("B1", "A1");

        assertThat(graph.hasCycle(ref("A1"))).isTrue();
        assertThat(graph.hasCycle(ref("B1"))).isTrue();
    }

    @Test
    @DisplayName("транзитивный цикл через три ячейки")
    void transitiveCycle() {
        formula("A1", "B1");
        formula("B1", "C1");
        formula("C1", "A1");

        assertThat(graph.hasCycle(ref("A1"))).isTrue();
        assertThat(graph.hasCycle(ref("C1"))).isTrue();
    }

    @Test
    @DisplayName("самоссылка: A1 = A1 + 1")
    void selfReference() {
        formula("A1", "A1");

        assertThat(graph.hasCycle(ref("A1"))).isTrue();
    }

    @Test
    @DisplayName("ациклический граф циклов не содержит")
    void acyclicGraph() {
        formula("B1", "A1");
        formula("C1", "B1");

        assertThat(graph.hasCycle(ref("A1"))).isFalse();
        assertThat(graph.hasCycle(ref("B1"))).isFalse();
        assertThat(graph.hasCycle(ref("C1"))).isFalse();
    }

    @Test
    @DisplayName("getDependents возвращает непосредственно зависящие ячейки")
    void dependents() {
        formula("B1", "A1");
        formula("C1", "A1");
        formula("D1", "B1");

        assertThat(graph.getDependents(ref("A1"))).containsExactlyInAnyOrder(ref("B1"), ref("C1"));
        assertThat(graph.getDependents(ref("B1"))).containsExactly(ref("D1"));
        assertThat(graph.getDependents(ref("D1"))).isEmpty();
        assertThat(graph.getPrecedents(ref("D1"))).containsExactly(ref("B1"));
    }

    @Test
    @DisplayName("каскадный пересчёт идёт по цепочке зависимостей")
    void cascadeOrder() {
        formula("B1", "A1");
        formula("C1", "B1");

        assertThat(order("A1")).containsExactly("B1", "C1");
        assertThat(order("B1")).containsExactly("C1");
        assertThat(order("C1")).isEmpty();
    }

    @Test
    @DisplayName("ячейка пересчитывается после всех, от которых зависит")
    void diamondOrder() {
        formula("B1", "A1");
        formula("C1", "A1");
        formula("D1", "B1", "C1");

        List<String> result = order("A1");

        assertThat(result).containsExactlyInAnyOrder("B1", "C1", "D1");
        assertThat(result.indexOf("D1")).isEqualTo(2);
    }

    @Test
    @DisplayName("removeDependencies убирает зависимости ячейки")
    void removeDependencies() {
        formula("B1", "A1");
        formula("C1", "B1");

        graph.removeDependencies(ref("B1"));

        assertThat(graph.getDependents(ref("A1"))).isEmpty();
        assertThat(graph.getPrecedents(ref("B1"))).isEmpty();
        assertThat(order("A1")).isEmpty();
        assertThat(order("B1")).containsExactly("C1");
    }

    @Test
    @DisplayName("removeDependencies разрывает цикл")
    void removeDependencyBreaksCycle() {
        formula("A1", "B1");
        formula("B1", "A1");

        graph.removeDependencies(ref("B1"));

        assertThat(graph.hasCycle(ref("A1"))).isFalse();
        assertThat(graph.hasCycle(ref("B1"))).isFalse();
    }

    @Test
    @DisplayName("ячейки цикла попадают в конец порядка пересчёта")
    void cycleParticipantsGoLast() {
        formula("B1", "A1");
        formula("C1", "B1", "D1");
        formula("D1", "C1");

        assertThat(order("A1")).containsExactly("B1", "C1", "D1");
    }

    @Test
    @DisplayName("полный порядок пересчёта включает все ячейки графа")
    void fullOrder() {
        formula("B1", "A1");
        formula("C1", "B1");

        assertThat(graph.topologicalOrder().stream().map(CellRef::toA1).toList())
                .containsExactly("A1", "B1", "C1");
    }

    @Test
    @DisplayName("для ячейки вне графа порядок пересчёта пуст")
    void unknownCell() {
        formula("B1", "A1");

        assertThat(order("Z9")).isEmpty();
        assertThat(graph.hasCycle(ref("Z9"))).isFalse();
    }
}
