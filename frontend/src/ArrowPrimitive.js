import { positionsBox } from './helpers/positions';

class ArrowRenderer {
    constructor(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;
            const hRatio = scope.horizontalPixelRatio;
            const vRatio = scope.verticalPixelRatio;

            this._data.arrows.forEach((arrow) => {
                // Skip arrows with invalid coordinates
                if (arrow.x === null || arrow.y === null) {
                    return;
                }

                const x = arrow.x * hRatio;
                const y = arrow.y * vRatio;
                const size = (arrow.size || 8) * Math.min(hRatio, vRatio);
                const color = arrow.color || '#2196F3';
                const direction = arrow.direction || 'up'; // 'up', 'down', 'left', 'right'

                ctx.fillStyle = color;
                ctx.strokeStyle = arrow.borderColor || color;
                ctx.lineWidth = (arrow.borderWidth || 1) * hRatio;

                // Draw arrow based on direction
                ctx.beginPath();
                
                switch (direction) {
                    case 'up':
                        // Triangle pointing up
                        ctx.moveTo(x, y - size);
                        ctx.lineTo(x - size * 0.7, y + size * 0.5);
                        ctx.lineTo(x + size * 0.7, y + size * 0.5);
                        break;
                    
                    case 'down':
                        // Triangle pointing down
                        ctx.moveTo(x, y + size);
                        ctx.lineTo(x - size * 0.7, y - size * 0.5);
                        ctx.lineTo(x + size * 0.7, y - size * 0.5);
                        break;
                    
                    case 'left':
                        // Triangle pointing left
                        ctx.moveTo(x - size, y);
                        ctx.lineTo(x + size * 0.5, y - size * 0.7);
                        ctx.lineTo(x + size * 0.5, y + size * 0.7);
                        break;
                    
                    case 'right':
                        // Triangle pointing right
                        ctx.moveTo(x + size, y);
                        ctx.lineTo(x - size * 0.5, y - size * 0.7);
                        ctx.lineTo(x - size * 0.5, y + size * 0.7);
                        break;
                    
                    case 'arrow-up':
                        // Arrow with stem pointing up
                        const stemWidth = size * 0.3;
                        ctx.moveTo(x, y - size);
                        ctx.lineTo(x - size * 0.7, y - size * 0.3);
                        ctx.lineTo(x - stemWidth, y - size * 0.3);
                        ctx.lineTo(x - stemWidth, y + size);
                        ctx.lineTo(x + stemWidth, y + size);
                        ctx.lineTo(x + stemWidth, y - size * 0.3);
                        ctx.lineTo(x + size * 0.7, y - size * 0.3);
                        break;
                    
                    case 'arrow-down':
                        // Arrow with stem pointing down
                        const stemWidth2 = size * 0.3;
                        ctx.moveTo(x, y + size);
                        ctx.lineTo(x - size * 0.7, y + size * 0.3);
                        ctx.lineTo(x - stemWidth2, y + size * 0.3);
                        ctx.lineTo(x - stemWidth2, y - size);
                        ctx.lineTo(x + stemWidth2, y - size);
                        ctx.lineTo(x + stemWidth2, y + size * 0.3);
                        ctx.lineTo(x + size * 0.7, y + size * 0.3);
                        break;
                }

                ctx.closePath();
                ctx.fill();
                
                if (arrow.borderWidth) {
                    ctx.stroke();
                }

                // Draw text label if provided
                if (arrow.text) {
                    ctx.fillStyle = arrow.textColor || '#ffffff';
                    ctx.font = `${10 * vRatio}px Arial`;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                    ctx.shadowBlur = 2 * vRatio;
                    
                    // Position text based on arrow direction
                    let textX = x;
                    let textY = y;
                    if (direction === 'up' || direction === 'arrow-up') {
                        textY = y - size - 8 * vRatio;
                    } else if (direction === 'down' || direction === 'arrow-down') {
                        textY = y + size + 8 * vRatio;
                    } else if (direction === 'left') {
                        textX = x - size - 8 * hRatio;
                    } else if (direction === 'right') {
                        textX = x + size + 8 * hRatio;
                    }
                    
                    ctx.fillText(arrow.text, textX, textY);
                    ctx.shadowBlur = 0;
                }
            });
        });
    }
}

class ArrowPaneView {
    constructor(source) {
        this._source = source;
        this._arrows = [];
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        this._arrows = this._source._arrowsData
            .map((arrow) => {
                let x = timeScale.timeToCoordinate(arrow.time);
                const y = series.priceToCoordinate(arrow.price);

                // Fallback: If timeToCoordinate returns null, use logical index
                if (x === null && chartData && chartData.length > 0) {
                    // Find logical index for the arrow time
                    const findLogicalIndex = (time) => {
                        let left = 0;
                        let right = chartData.length - 1;
                        
                        while (left <= right) {
                            const mid = Math.floor((left + right) / 2);
                            if (chartData[mid].time === time) return mid;
                            if (chartData[mid].time < time) {
                                left = mid + 1;
                            } else {
                                right = mid - 1;
                            }
                        }
                        return left < chartData.length ? left : chartData.length - 1;
                    };
                    
                    const logical = findLogicalIndex(arrow.time);
                    if (logical !== null) {
                        x = timeScale.logicalToCoordinate(logical);
                    }
                }

                return {
                    x,
                    y,
                    size: arrow.size,
                    color: arrow.color,
                    borderColor: arrow.borderColor,
                    borderWidth: arrow.borderWidth,
                    direction: arrow.direction,
                    text: arrow.text,
                    textColor: arrow.textColor,
                };
            })
            .filter(arrow => {
                // Skip arrows with null coordinates
                return arrow.x !== null && arrow.y !== null;
            });
    }

    renderer() {
        return new ArrowRenderer({
            arrows: this._arrows || [],
        });
    }
}

export class ArrowPrimitive {
    constructor(chart, series, arrowsData, chartData) {
        this._chart = chart;
        this._series = series;
        this._arrowsData = arrowsData;
        this._chartData = chartData;
        this._paneViews = [new ArrowPaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include arrow prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._arrowsData.forEach(arrow => {
            if (arrow.price < minPrice) minPrice = arrow.price;
            if (arrow.price > maxPrice) maxPrice = arrow.price;
        });

        if (minPrice === Infinity || maxPrice === -Infinity) {
            return null;
        }

        return {
            priceRange: {
                minValue: minPrice,
                maxValue: maxPrice,
            },
        };
    }
}

