/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.functiongraph.graph.layout;

import org.junit.Test;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DNLEdgeLabelRendererTest {

    @Test
    public void testClearAnchorStaysInPlace() {
        Point2D preferred = new Point2D.Double(40, 40);
        assertEquals(preferred, DNLEdgeLabelRenderer.avoidVertexOverlap(preferred,
                new Dimension(60, 20), List.of(new Rectangle2D.Double(100, 100, 200, 80))));
    }

    @Test
    public void testPartlyHiddenLabelMovesJustAboveCard() {
        // The fixture's label starts above its destination, but its bottom is behind the card.
        Point2D preferred = new Point2D.Double(604, 466);
        List<Rectangle2D> cards = List.of(new Rectangle2D.Double(606, 475, 210, 90));
        Point2D result = placeAndCheck(preferred, new Dimension(52, 20), cards);
        assertEquals(new Point2D.Double(604, 455), result);
        assertEquals(new Point2D.Double(604, 466), preferred);
    }

    @Test
    public void testClearsNeighborAsWellAsDestination() {
        List<Rectangle2D> cards = List.of(new Rectangle2D.Double(100, 100, 100, 100),
                new Rectangle2D.Double(100, 50, 100, 45));
        Point2D result = placeAndCheck(new Point2D.Double(125, 95), new Dimension(50, 20), cards);
        assertEquals(new Point2D.Double(125, 30), result);
    }

    @Test
    public void testLongLabelInDenseCardsHasClearPlacement() {
        List<Rectangle2D> cards = List.of(new Rectangle2D.Double(0, 0, 100, 100),
                new Rectangle2D.Double(110, 0, 100, 100),
                new Rectangle2D.Double(0, 110, 210, 100));
        placeAndCheck(new Point2D.Double(90, 95), new Dimension(350, 24), cards);
    }

    @Test
    public void testMovedLabelClearsPreviouslyPlacedLabel() {
        List<Rectangle2D> occupied = List.of(new Rectangle2D.Double(606, 475, 210, 90),
                new Rectangle2D.Double(530, 450, 60, 25),
                new Rectangle2D.Double(200, 260, 430, 190));
        placeAndCheck(new Point2D.Double(604, 466), new Dimension(52, 20), occupied);
    }

    @Test
    public void testPannedFractionalCoordinatesAndEmptyGraph() {
        Point2D preferred = new Point2D.Double(-100.5, -70.25);
        Dimension size = new Dimension(52, 20);
        placeAndCheck(preferred, size, List.of(new Rectangle2D.Double(-99.5, -61.75, 120, 90)));
        assertEquals(preferred, DNLEdgeLabelRenderer.avoidVertexOverlap(preferred, size, List.of()));
    }

    private Point2D placeAndCheck(Point2D preferred, Dimension size, List<Rectangle2D> cards) {
        Point2D result = DNLEdgeLabelRenderer.avoidVertexOverlap(preferred, size, cards);
        Rectangle2D label = new Rectangle2D.Double(result.getX(), result.getY(), size.width, size.height);
        for (Rectangle2D card : cards) {
            assertFalse("Label overlaps " + card, label.intersects(card));
        }
        return result;
    }
}
