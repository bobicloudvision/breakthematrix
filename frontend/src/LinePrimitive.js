import { positionsBox } from './helpers/positions';

class LineRenderer {
    constructor(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;

            this._data.lines.forEach((line) => {
                // Skip lines with invalid coordinates
                if (line.x1 === null || line.x2 === null || line.y1 === null || line.y2 === null) {
                    return;
                }

                const x1 = line.x1 * scope.horizontalPixelRatio;
                const y1 = line.y1 * scope.verticalPixelRatio;
                const x2 = line.x2 * scope.horizontalPixelRatio;
                const y2 = line.y2 * scope.verticalPixelRatio;

                // Draw line
                ctx.strokeStyle = line.color || '#2196F3';
                ctx.lineWidth = (line.lineWidth || 1) * scope.horizontalPixelRatio;
                
                // Set line style
                if (line.lineStyle === 'dashed') {
                    ctx.setLineDash([5 * scope.horizontalPixelRatio, 5 * scope.horizontalPixelRatio]);
                } else if (line.lineStyle === 'dotted') {
                    ctx.setLineDash([2 * scope.horizontalPixelRatio, 2 * scope.horizontalPixelRatio]);
                } else {
                    ctx.setLineDash([]);
                }
                
                ctx.beginPath();
                ctx.moveTo(x1, y1);
                ctx.lineTo(x2, y2);
                ctx.stroke();

                // Draw label if provided
                if (line.label) {
                    ctx.fillStyle = line.color || '#2196F3';
                    ctx.font = `${10 * scope.verticalPixelRatio}px Arial`;
                    ctx.textBaseline = 'bottom';
                    ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                    ctx.shadowBlur = 2 * scope.verticalPixelRatio;
                    
                    // Position label at the start of the line, slightly above
                    ctx.fillText(
                        line.label,
                        x1 + 4 * scope.horizontalPixelRatio,
                        y1 - 4 * scope.verticalPixelRatio
                    );
                    ctx.shadowBlur = 0;
                }
                
                // Reset line dash
                ctx.setLineDash([]);
            });
        });
    }
}

class LinePaneView {
    constructor(source) {
        this._source = source;
        this._lines = []; // Initialize empty array
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        this._lines = this._source._linesData.map((line) => {
            let x1 = timeScale.timeToCoordinate(line.time1);
            let x2 = timeScale.timeToCoordinate(line.time2);
            const y1 = series.priceToCoordinate(line.price1);
            const y2 = series.priceToCoordinate(line.price2);

            // Fallback: If timeToCoordinate returns null, use logical indices
            if ((x1 === null || x2 === null) && chartData && chartData.length > 0) {
                let logical1 = null;
                let logical2 = null;
                
                // Binary search for better performance with large datasets
                // Find logical indices for the line times
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
                
                logical1 = findLogicalIndex(line.time1);
                logical2 = findLogicalIndex(line.time2);
                
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
                color: line.color,
                lineWidth: line.lineWidth,
                lineStyle: line.lineStyle,
                label: line.label,
            };
        });
    }

    renderer() {
        return new LineRenderer({
            lines: this._lines || [],
        });
    }
}

export class LinePrimitive {
    constructor(chart, series, linesData, chartData) {
        this._chart = chart;
        this._series = series;
        this._linesData = linesData;
        this._chartData = chartData; // Main chart data for logical coordinate fallback
        this._paneViews = [new LinePaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include line prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._linesData.forEach(line => {
            const minLinePrice = Math.min(line.price1, line.price2);
            const maxLinePrice = Math.max(line.price1, line.price2);
            if (minLinePrice < minPrice) minPrice = minLinePrice;
            if (maxLinePrice > maxPrice) maxPrice = maxLinePrice;
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

