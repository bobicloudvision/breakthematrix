import { positionsBox } from './helpers/positions';

class BoxRenderer {
    constructor(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;

            this._data.boxes.forEach((box) => {
                // Skip boxes with invalid coordinates
                if (box.x1 === null || box.x2 === null || box.y1 === null || box.y2 === null) {
                    return;
                }

                const horizontalPositions = positionsBox(
                    box.x1,
                    box.x2,
                    scope.horizontalPixelRatio
                );
                const verticalPositions = positionsBox(
                    box.y1,
                    box.y2,
                    scope.verticalPixelRatio
                );

                // Draw background
                ctx.fillStyle = box.backgroundColor || 'rgba(33, 150, 243, 0.1)';
                ctx.fillRect(
                    horizontalPositions.position,
                    verticalPositions.position,
                    horizontalPositions.length,
                    verticalPositions.length
                );

                // Draw border only if border color or width is explicitly provided
                if (box.borderColor || box.borderWidth) {
                    ctx.strokeStyle = box.borderColor || '#2196F3';
                    ctx.lineWidth = (box.borderWidth || 1) * scope.horizontalPixelRatio;
                    
                    if (box.borderStyle === 'dashed') {
                        ctx.setLineDash([5 * scope.horizontalPixelRatio, 5 * scope.horizontalPixelRatio]);
                    } else if (box.borderStyle === 'dotted') {
                        ctx.setLineDash([2 * scope.horizontalPixelRatio, 2 * scope.horizontalPixelRatio]);
                    } else {
                        ctx.setLineDash([]);
                    }
                    
                    ctx.strokeRect(
                        horizontalPositions.position,
                        verticalPositions.position,
                        horizontalPositions.length,
                        verticalPositions.length
                    );
                }

                // Draw text
                if (box.text) {
                    ctx.fillStyle = box.textColor || '#ffffff';
                    ctx.font = `bold ${11 * scope.verticalPixelRatio}px Arial`;
                    ctx.textBaseline = 'top';
                    ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                    ctx.shadowBlur = 2 * scope.verticalPixelRatio;
                    ctx.fillText(
                        box.text,
                        horizontalPositions.position + 4 * scope.horizontalPixelRatio,
                        verticalPositions.position + 4 * scope.verticalPixelRatio
                    );
                    ctx.shadowBlur = 0;
                }
            });
        });
    }
}

class BoxPaneView {
    constructor(source) {
        this._source = source;
        this._boxes = []; // Initialize empty array
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        this._boxes = this._source._boxesData.map((box) => {
            let x1 = timeScale.timeToCoordinate(box.time1);
            let x2 = timeScale.timeToCoordinate(box.time2);
            const y1 = series.priceToCoordinate(box.price1);
            const y2 = series.priceToCoordinate(box.price2);

            // Fallback: If timeToCoordinate returns null, use logical indices
            if ((x1 === null || x2 === null) && chartData && chartData.length > 0) {
                let logical1 = null;
                let logical2 = null;
                
                // Binary search for better performance with large datasets
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
                
                logical1 = findLogicalIndex(box.time1);
                logical2 = findLogicalIndex(box.time2);
                
                // Convert logical indices to coordinates
                if (logical1 !== null) {
                    x1 = timeScale.logicalToCoordinate(logical1);
                }
                if (logical2 !== null) {
                    x2 = timeScale.logicalToCoordinate(logical2);
                }
            }

            return {
                x1,
                x2,
                y1,
                y2,
                backgroundColor: box.backgroundColor,
                borderColor: box.borderColor,
                borderWidth: box.borderWidth,
                borderStyle: box.borderStyle,
                text: box.text,
                textColor: box.textColor,
            };
        });
    }

    renderer() {
        return new BoxRenderer({
            boxes: this._boxes || [],
        });
    }
}

export class BoxPrimitive {
    constructor(chart, series, boxesData, chartData) {
        this._chart = chart;
        this._series = series;
        this._boxesData = boxesData;
        this._chartData = chartData; // Main chart data for logical coordinate fallback
        this._paneViews = [new BoxPaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include box prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._boxesData.forEach(box => {
            const minBoxPrice = Math.min(box.price1, box.price2);
            const maxBoxPrice = Math.max(box.price1, box.price2);
            if (minBoxPrice < minPrice) minPrice = minBoxPrice;
            if (maxBoxPrice > maxPrice) maxPrice = maxBoxPrice;
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

