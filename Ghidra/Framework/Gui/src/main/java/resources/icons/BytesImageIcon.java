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
package resources.icons;

import generic.util.image.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * {@link LazyImageIcon} that is created from a byte array
 */
public class BytesImageIcon extends LazyImageIcon {
    private byte[] bytes;

    public BytesImageIcon(String name, byte[] imageBytes) {
        super(name);
        this.bytes = Objects.requireNonNull(imageBytes);
    }

    @Override
    protected ImageIcon createImageIcon() {
        String name = getFilename();
        Image image = createImage();
        if (!ImageUtils.waitForImage(name, image)) {
            return null;
        }
        return new ImageIcon(image, name);
    }

    protected Image createImage() {
        return Toolkit.getDefaultToolkit().createImage(bytes);
    }
}
