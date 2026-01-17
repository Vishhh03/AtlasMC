#version 150
/* 
    WRP (World Resource Pack) shader for lightmap rendering
    Fullbright UB
*/

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // 100% Fullbright
    fragColor = vec4(1.0);
}
